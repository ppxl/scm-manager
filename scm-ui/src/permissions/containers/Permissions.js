//@flow
import React from "react";
import { connect } from "react-redux";
import { translate } from "react-i18next";
import {
  fetchPermissions,
  getFetchPermissionsFailure,
  isFetchPermissionsPending,
  getPermissionsOfRepo,
  hasCreatePermission,
  createPermission,
  isCreatePermissionPending,
  getCreatePermissionFailure,
  createPermissionReset,
  getDeletePermissionsFailure,
  getModifyPermissionsFailure,
  modifyPermissionReset,
  deletePermissionReset
} from "../modules/permissions";
import { Loading, ErrorPage } from "@scm-manager/ui-components";
import type {
  Permission,
  PermissionCollection,
  PermissionEntry
} from "../types/Permissions";
import SinglePermission from "./SinglePermission";
import CreatePermissionForm from "../components/CreatePermissionForm";
import type { History } from "history";

type Props = {
  namespace: string,
  repoName: string,
  loading: boolean,
  error: Error,
  permissions: PermissionCollection,
  hasPermissionToCreate: boolean,
  loadingCreatePermission: boolean,

  //dispatch functions
  fetchPermissions: (namespace: string, repoName: string) => void,
  createPermission: (
    permission: PermissionEntry,
    namespace: string,
    repoName: string,
    callback?: () => void
  ) => void,
  createPermissionReset: (string, string) => void,
  modifyPermissionReset: (string, string) => void,
  deletePermissionReset: (string, string) => void,
  // context props
  t: string => string,
  match: any,
  history: History
};

class Permissions extends React.Component<Props> {
  componentDidMount() {
    const {
      fetchPermissions,
      namespace,
      repoName,
      modifyPermissionReset,
      createPermissionReset,
      deletePermissionReset
    } = this.props;

    createPermissionReset(namespace, repoName);
    modifyPermissionReset(namespace, repoName);
    deletePermissionReset(namespace, repoName);
    fetchPermissions(namespace, repoName);
  }

  createPermission = (permission: Permission) => {
    this.props.createPermission(
      permission,
      this.props.namespace,
      this.props.repoName
    );
  };

  render() {
    const {
      loading,
      error,
      permissions,
      t,
      namespace,
      repoName,
      loadingCreatePermission,
      hasPermissionToCreate
    } = this.props;
    if (error) {
      return (
        <ErrorPage
          title={t("permissions.error-title")}
          subtitle={t("permissions.error-subtitle")}
          error={error}
        />
      );
    }

    if (loading || !permissions) {
      return <Loading />;
    }

    const createPermissionForm = hasPermissionToCreate ? (
      <CreatePermissionForm
        createPermission={permission => this.createPermission(permission)}
        loading={loadingCreatePermission}
        currentPermissions={permissions}
      />
    ) : null;

    return (
      <div>
        <table className="table is-hoverable is-fullwidth">
          <thead>
            <tr>
              <th>{t("permission.name")}</th>
              <th className="is-hidden-mobile">{t("permission.type")}</th>
              <th>{t("permission.group-permission")}</th>
            </tr>
          </thead>
          <tbody>
            {permissions.map(permission => {
              return (
                <SinglePermission
                  key={permission.name}
                  namespace={namespace}
                  repoName={repoName}
                  permission={permission}
                />
              );
            })}
          </tbody>
        </table>
        {createPermissionForm}
      </div>
    );
  }
}

const mapStateToProps = (state, ownProps) => {
  const namespace = ownProps.namespace;
  const repoName = ownProps.repoName;
  const error =
    getFetchPermissionsFailure(state, namespace, repoName) ||
    getCreatePermissionFailure(state, namespace, repoName) ||
    getDeletePermissionsFailure(state, namespace, repoName) ||
    getModifyPermissionsFailure(state, namespace, repoName);
  const loading = isFetchPermissionsPending(state, namespace, repoName);
  const permissions = getPermissionsOfRepo(state, namespace, repoName);
  const loadingCreatePermission = isCreatePermissionPending(
    state,
    namespace,
    repoName
  );
  const hasPermissionToCreate = hasCreatePermission(state, namespace, repoName);
  return {
    namespace,
    repoName,
    error,
    loading,
    permissions,
    hasPermissionToCreate,
    loadingCreatePermission
  };
};

const mapDispatchToProps = dispatch => {
  return {
    fetchPermissions: (namespace: string, repoName: string) => {
      dispatch(fetchPermissions(namespace, repoName));
    },
    createPermission: (
      permission: PermissionEntry,
      namespace: string,
      repoName: string,
      callback?: () => void
    ) => {
      dispatch(createPermission(permission, namespace, repoName, callback));
    },
    createPermissionReset: (namespace: string, repoName: string) => {
      dispatch(createPermissionReset(namespace, repoName));
    },
    modifyPermissionReset: (namespace: string, repoName: string) => {
      dispatch(modifyPermissionReset(namespace, repoName));
    },
    deletePermissionReset: (namespace: string, repoName: string) => {
      dispatch(deletePermissionReset(namespace, repoName));
    }
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(translate("permissions")(Permissions));
