/**
 * Copyright (c) 2018, Sebastian Sdorra
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of SCM-Manager; nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * http://bitbucket.org/sdorra/scm-manager
 *
 */


package sonia.scm.web;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sonia.scm.util.HttpUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

/**
 * WireProtocol provides methods for handling the mercurial wire protocol.
 *
 * @see <a href="https://goo.gl/WaVJzw">Mercurial Wire Protocol</a>
 */
public final class WireProtocol {

  private static final Logger LOG = LoggerFactory.getLogger(WireProtocol.class);

  private static final Set<String> READ_COMMANDS = ImmutableSet.of(
    "batch", "between", "branchmap", "branches", "capabilities", "changegroup", "changegroupsubset", "clonebundles",
    "getbundle", "heads", "hello", "listkeys", "lookup", "known", "stream_out",
    // could not find lheads in the wireprotocol description but mercurial 4.5.2 uses it for clone
    "lheads"
  );

  private static final Set<String> WRITE_COMMANDS = ImmutableSet.of(
    "pushkey", "unbundle"
  );

  private WireProtocol() {
  }

  /**
   * Returns {@code true} if the request is a write request. The method will always return {@code true}, expect for the
   * following cases:
   *
   * - no command was specified with the request (is required for the hgweb ui)
   * - the command in the query string was found in the list of read request
   * - if query string contains the batch command, then all commands specified in X-HgArg headers must be
   *   in the list of read requests
   * - in case of enabled HttpPostArgs protocol and query string container the batch command, the header X-HgArgs-Post
   *   is read and the commands which are specified in the body from 0 to the value of X-HgArgs-Post must be in the list
   *   of read requests
   *
   * @param request http request
   *
   * @return {@code true} for write requests.
   */
  public static boolean isWriteRequest(HttpServletRequest request) {
    List<String> commands = commandsOf(request);
    boolean write = isWriteRequest(commands);
    LOG.trace("mercurial request {} is write: {}", commands, write);
    return write;
  }

  @VisibleForTesting
  static boolean isWriteRequest(List<String> commands) {
    return !READ_COMMANDS.containsAll(commands);
  }

  @VisibleForTesting
  static List<String> commandsOf(HttpServletRequest request) {
    List<String> listOfCmds = Lists.newArrayList();

    String cmd = getCommandFromQueryString(request);
    if (cmd != null) {
      listOfCmds.add(cmd);
      if (isBatchCommand(cmd)) {
        parseHgArgHeaders(request, listOfCmds);
        handleHttpPostArgs(request, listOfCmds);
      }
    }
    return Collections.unmodifiableList(listOfCmds);
  }

  private static void handleHttpPostArgs(HttpServletRequest request, List<String> listOfCmds) {
    int hgArgsPostSize = request.getIntHeader("X-HgArgs-Post");
    if (hgArgsPostSize > 0) {

      if (request instanceof HgServletRequest) {
        HgServletRequest hgRequest = (HgServletRequest) request;

        parseHttpPostArgs(listOfCmds, hgArgsPostSize, hgRequest);
      } else {
        throw new IllegalArgumentException("could not process the httppostargs protocol without HgServletRequest");
      }

    }
  }

  private static void parseHttpPostArgs(List<String> listOfCmds, int hgArgsPostSize, HgServletRequest hgRequest) {
    try {
      byte[] bytes = hgRequest.getInputStream().readAndCapture(hgArgsPostSize);
      // we use iso-8859-1 for encoding, because the post args are normally http headers which are using iso-8859-1
      // see https://tools.ietf.org/html/rfc7230#section-3.2.4
      String hgArgs = new String(bytes, Charsets.ISO_8859_1);
      String decoded = decodeValue(hgArgs);
      parseHgCommandHeader(listOfCmds, decoded);
    } catch (IOException ex) {
      throw Throwables.propagate(ex);
    }
  }

  private static void parseHgArgHeaders(HttpServletRequest request, List<String> listOfCmds) {
    Enumeration headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String header = (String) headerNames.nextElement();
      parseHgArgHeader(request, listOfCmds, header);
    }
  }

  private static void parseHgArgHeader(HttpServletRequest request, List<String> listOfCmds, String header) {
    if (isHgArgHeader(header)) {
      String value = getHeaderDecoded(request, header);
      parseHgArgValue(listOfCmds, value);
    }
  }

  private static void parseHgArgValue(List<String> listOfCmds, String value) {
    if (isHgArgCommandHeader(value)) {
      parseHgCommandHeader(listOfCmds, value);
    }
  }

  private static void parseHgCommandHeader(List<String> listOfCmds, String value) {
    String[] cmds = value.substring(5).split(";");
    for (String cmd : cmds ) {
      String normalizedCmd = normalize(cmd);
      int index = normalizedCmd.indexOf(' ');
      if (index > 0) {
        listOfCmds.add(normalizedCmd.substring(0, index));
      } else {
        listOfCmds.add(normalizedCmd);
      }
    }
  }

  private static String normalize(String cmd) {
    return cmd.trim().toLowerCase(Locale.ENGLISH);
  }

  private static boolean isHgArgCommandHeader(String value) {
    return value.startsWith("cmds=");
  }

  private static String getHeaderDecoded(HttpServletRequest request, String header) {
    return decodeValue(request.getHeader(header));
  }

  private static String decodeValue(String value) {
    return HttpUtil.decode(Strings.nullToEmpty(value));
  }

  private static boolean isHgArgHeader(String header) {
    return header.toLowerCase(Locale.ENGLISH).startsWith("x-hgarg-");
  }

  private static boolean isBatchCommand(String cmd) {
    return "batch".equalsIgnoreCase(cmd);
  }

  private static String getCommandFromQueryString(HttpServletRequest request) {
    // we can't use getParameter, because this would inspect the body for form parameters as well
    Multimap<String, String> queryParameterMap = createQueryParameterMap(request);

    Collection<String> cmd = queryParameterMap.get("cmd");
    Preconditions.checkArgument(cmd.size() <= 1, "found more than one cmd query parameter");
    Iterator<String> iterator = cmd.iterator();

    String command = null;
    if (iterator.hasNext()) {
      command = iterator.next();
    }
    return command;
  }

  private static Multimap<String,String> createQueryParameterMap(HttpServletRequest request) {
    Multimap<String,String> parameterMap = HashMultimap.create();

    String queryString = request.getQueryString();
    if (!Strings.isNullOrEmpty(queryString)) {

      String[] parameters = queryString.split("&");
      for (String parameter : parameters) {
        int index = parameter.indexOf('=');
        if (index > 0) {
          parameterMap.put(parameter.substring(0, index), parameter.substring(index + 1));
        } else {
          parameterMap.put(parameter, "true");
        }
      }

    }

    return parameterMap;
  }
}
