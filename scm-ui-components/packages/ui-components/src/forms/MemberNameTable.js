//@flow
import React from "react";
import { translate } from "react-i18next";
import RemoveEntryOfTableButton from "../buttons/RemoveEntryOfTableButton";
import TagGroup from "../TagGroup";

type Props = {
  members: string[],
  memberListChanged: (string[]) => void,
  t: string => string
};

class MemberNameTable extends React.Component<Props> {
  render() {
    const { members, t } = this.props;

    return (
      <TagGroup
        items={members}
        label={t("group.members")}
        helpText={t("groupForm.help.memberHelpText")}
        onRemove={() => this.removeEntry}
      />
    );
  }

  removeEntry = (member: string[]) => {
    this.props.memberListChanged(member);
  };
}

export default translate("groups")(MemberNameTable);
