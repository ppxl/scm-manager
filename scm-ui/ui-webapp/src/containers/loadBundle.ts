/*
 * MIT License
 *
 * Copyright (c) 2020-present Cloudogu GmbH and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/* global SystemJS */
// eslint-disable-next-line import/no-webpack-loader-syntax
import "script-loader!../../../../node_modules/systemjs/dist/system.js";

import * as React from "react";
import * as ReactDOM from "react-dom";
import * as ReactRouterDom from "react-router-dom";
import * as Redux from "redux";
import * as ReactRedux from "react-redux";
import SytleComponentsDefault, * as SytleComponents from "styled-components";
import * as ReactI18Next from "react-i18next";
import ClassNamesDefault, * as ClassNames from "classnames";
import QueryStringDefault, * as QueryString from "query-string";
import * as UIExtensions from "@scm-manager/ui-extensions";
import * as UIComponents from "@scm-manager/ui-components";
import { urls } from "@scm-manager/ui-components";

type PluginModule = {
  name: string;
  address: string;
};

const BundleLoader = {
  name: "bundle-loader",
  fetch: (plugin: PluginModule) => {
    let url = plugin.address;
    if (!url.endsWith(".bundle.js")) {
      url += ".bundle.js";
    }

    if (url.includes("@scm-manager/")) {
      url = url.replace("@scm-manager/", "");
    }
    return fetch(url, {
      credentials: "same-origin",
      headers: {
        Cache: "no-cache",
        // identify the request as ajax request
        "X-Requested-With": "XMLHttpRequest"
      }
    }).then(response => {
      return response.text();
    });
  }
};

SystemJS.registry.set(BundleLoader.name, SystemJS.newModule(BundleLoader));

SystemJS.config({
  baseURL: urls.withContextPath("/assets"),
  meta: {
    "/*": {
      // @ts-ignore typing missing, but seems required
      esModule: true,
      authorization: true,
      loader: BundleLoader.name
    }
  }
});

const expose = (name: string, cmp: any, defaultCmp?: any) => {
  let mod = cmp;
  if (defaultCmp) {
    // SystemJS default export:
    // https://github.com/systemjs/systemjs/issues/1749
    mod = {
      ...cmp,
      __useDefault: defaultCmp
    };
  }
  SystemJS.set(name, SystemJS.newModule(mod));
};

expose("react", React);
expose("react-dom", ReactDOM);
expose("react-router-dom", ReactRouterDom);
expose("styled-components", SytleComponents, SytleComponentsDefault);
expose("redux", Redux);
expose("react-redux", ReactRedux);
expose("react-i18next", ReactI18Next);
expose("classnames", ClassNames, ClassNamesDefault);
expose("query-string", QueryString, QueryStringDefault);
expose("@scm-manager/ui-extensions", UIExtensions);
expose("@scm-manager/ui-components", UIComponents);

export default (plugin: string) => SystemJS.import(plugin);
