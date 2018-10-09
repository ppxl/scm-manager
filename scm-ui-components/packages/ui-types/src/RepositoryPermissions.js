//@flow
import type { Links } from "./hal";

export type Permission = {
  name: string,
  type: string,
  groupPermission: boolean,
  _links?: Links
};

export type PermissionEntry = {
  name: string,
  type: string,
  groupPermission: boolean
}

export type PermissionCollection = Permission[];
