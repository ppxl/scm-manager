import React, { Component } from "react";
import Main from "./Main";
import { connect } from "react-redux";
import { withRouter } from "react-router-dom";
import { fetchMe } from "../modules/auth";

import "./App.css";
import "../components/ConfirmAlert.css";
import Header from "../components/Header";
import PrimaryNavigation from "../components/PrimaryNavigation";
import Loading from "../components/Loading";
import ErrorPage from "../components/ErrorPage";
import Footer from "../components/Footer";

type Props = {
  me: any,
  error: Error,
  loading: boolean,
  authenticated?: boolean,
  fetchMe: () => void
};

class App extends Component<Props> {
  componentDidMount() {
    this.props.fetchMe();
  }

  render() {
    const { entry, loading, error, authenticated } = this.props;

    let content;
    const navigation = authenticated ? <PrimaryNavigation /> : "";

    if (loading) {
      content = <Loading />;
    } else if (error) {
      content = (
        <ErrorPage
          title="Error"
          subtitle="Unknown error occurred"
          error={error}
        />
      );
    } else {
      content = <Main authenticated={authenticated} />;
    }
    return (
      <div className="App">
        <Header>{navigation}</Header>
        {content}
        <Footer me={entry} />
      </div>
    );
  }
}

const mapDispatchToProps = (dispatch: any) => {
  return {
    fetchMe: () => dispatch(fetchMe())
  };
};

const mapStateToProps = state => {
  let mapped = state.auth.me || {};
  if (state.auth.login) {
    mapped.authenticated = state.auth.login.authenticated;
  }
  return mapped;
};

export default withRouter(
  connect(
    mapStateToProps,
    mapDispatchToProps
  )(App)
);
