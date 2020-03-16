/**
 * Copyright (c) 2014, Sebastian Sdorra
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
package sonia.scm.security;

import com.google.common.collect.ImmutableSet;
import sonia.scm.plugin.Extension;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Validates xsrf protected access tokens. The validator check if the current request contains an xsrf key which is
 * equal to the one in the access token. If the token does not contain a xsrf key, the check is passed by. The xsrf keys
 * are added by the {@link XsrfAccessTokenEnricher}.
 * 
 * @author Sebastian Sdorra
 * @since 2.0.0
 */
@Extension
public class XsrfAccessTokenValidator implements AccessTokenValidator {

  private static final Set<String> ALLOWED_METHOD = ImmutableSet.of(
    "GET", "HEAD", "OPTIONS"
  );

  private final Provider<HttpServletRequest> requestProvider;

  
  /**
   * Constructs a new instance.
   * 
   * @param requestProvider http request provider
   */
  @Inject
  public XsrfAccessTokenValidator(Provider<HttpServletRequest> requestProvider) {
    this.requestProvider = requestProvider;
  }
  
  @Override
  public boolean validate(AccessToken accessToken) {
    Optional<String> xsrfClaim = accessToken.getCustom(Xsrf.TOKEN_KEY);
    if (xsrfClaim.isPresent()) {
      HttpServletRequest request = requestProvider.get();
      String xsrfHeaderValue = request.getHeader(Xsrf.HEADER_KEY);
      return ALLOWED_METHOD.contains(request.getMethod().toUpperCase(Locale.ENGLISH))
        || xsrfClaim.get().equals(xsrfHeaderValue);
    }
    return true;
  }
  
}