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
import React from "react";
import { withTranslation, WithTranslation } from "react-i18next";
import classNames from "classnames";
import styled from "styled-components";
// @ts-ignore
import { Decoration, getChangeKey, Hunk } from "react-diff-view";
import { ButtonGroup } from "../buttons";
import Tag from "../Tag";
import Icon from "../Icon";
import { Change, ChangeEvent, DiffObjectProps, File, Hunk as HunkType } from "./DiffTypes";
import TokenizedDiffView from "./TokenizedDiffView";
import DiffButton from "./DiffButton";
import { MenuContext } from "@scm-manager/ui-components";
import DiffExpander, { ExpandableHunk } from "./DiffExpander";

const EMPTY_ANNOTATION_FACTORY = {};

type Props = DiffObjectProps &
  WithTranslation & {
    file: File;
  };

type Collapsible = {
  collapsed?: boolean;
};

type State = Collapsible & {
  sideBySide?: boolean;
  diffExpander: DiffExpander;
};

const DiffFilePanel = styled.div`
  /* remove bottom border for collapsed panels */
  ${(props: Collapsible) => (props.collapsed ? "border-bottom: none;" : "")};
`;

const FlexWrapLevel = styled.div`
  /* breaks into a second row
     when buttons and title become too long */
  flex-wrap: wrap;
`;

const FullWidthTitleHeader = styled.div`
  max-width: 100%;
`;

const TitleWrapper = styled.span`
  margin-left: 0.25rem;
`;

const ButtonWrapper = styled.div`
  /* align child to right */
  margin-left: auto;
`;

const HunkDivider = styled.div`
  background: #33b2e8;
  font-size: 0.7rem;
`;

const ChangeTypeTag = styled(Tag)`
  margin-left: 0.75rem;
`;

class DiffFile extends React.Component<Props, State> {
  static defaultProps: Partial<Props> = {
    defaultCollapse: false,
    markConflicts: true
  };

  constructor(props: Props) {
    super(props);
    this.state = {
      collapsed: this.defaultCollapse(),
      sideBySide: props.sideBySide,
      diffExpander: new DiffExpander(props.file)
    };
  }

  componentDidUpdate(prevProps: Readonly<Props>, prevState: Readonly<State>, snapshot?: any): void {
    if (this.props.defaultCollapse !== prevProps.defaultCollapse) {
      this.setState({
        collapsed: this.defaultCollapse()
      });
    }
  }

  defaultCollapse: () => boolean = () => {
    const { defaultCollapse, file } = this.props;
    if (typeof defaultCollapse === "boolean") {
      return defaultCollapse;
    } else if (typeof defaultCollapse === "function") {
      return defaultCollapse(file.oldPath, file.newPath);
    } else {
      return false;
    }
  };

  toggleCollapse = () => {
    const { file } = this.props;
    if (this.hasContent(file)) {
      this.setState(state => ({
        collapsed: !state.collapsed
      }));
    }
  };

  toggleSideBySide = (callback: () => void) => {
    this.setState(
      state => ({
        sideBySide: !state.sideBySide
      }),
      () => callback()
    );
  };

  setCollapse = (collapsed: boolean) => {
    this.setState({
      collapsed
    });
  };

  createHunkHeader = (expandableHunk: ExpandableHunk) => {
    if (expandableHunk.maxExpandHeadRange > 0) {
      return (
        <Decoration>
          <HunkDivider onClick={() => this.setState({ diffExpander: expandableHunk.expandHead() })}>
            {`Load ${expandableHunk.maxExpandHeadRange} more lines`}
          </HunkDivider>
        </Decoration>
      );
    }
    // hunk header must be defined
    return <span />;
  };

  createHunkFooter = (expandableHunk: ExpandableHunk) => {
    if (expandableHunk.maxExpandBottomRange > 0) {
      return (
        <Decoration>
          <HunkDivider onClick={() => this.setState({ diffExpander: expandableHunk.expandBottom() })}>
            {`Load ${expandableHunk.maxExpandBottomRange} more lines`}
          </HunkDivider>
        </Decoration>
      );
    }
    // hunk header must be defined
    return <span />;
  };

  collectHunkAnnotations = (hunk: HunkType) => {
    const { annotationFactory, file } = this.props;
    if (annotationFactory) {
      return annotationFactory({
        hunk,
        file
      });
    } else {
      return EMPTY_ANNOTATION_FACTORY;
    }
  };

  handleClickEvent = (change: Change, hunk: HunkType) => {
    const { file, onClick } = this.props;
    const context = {
      changeId: getChangeKey(change),
      change,
      hunk,
      file
    };
    if (onClick) {
      onClick(context);
    }
  };

  createGutterEvents = (hunk: HunkType) => {
    const { onClick } = this.props;
    if (onClick) {
      return {
        onClick: (event: ChangeEvent) => {
          this.handleClickEvent(event.change, hunk);
        }
      };
    }
  };

  renderHunk = (file: File, expandableHunk: ExpandableHunk, i: number) => {
    const hunk = expandableHunk.hunk;
    if (this.props.markConflicts && hunk.changes) {
      this.markConflicts(hunk);
    }
    const items = [];
    if (file._links?.lines) {
      items.push(this.createHunkHeader(expandableHunk));
    }
    items.push(
      <Hunk
        key={"hunk-" + hunk.content}
        hunk={expandableHunk.hunk}
        widgets={this.collectHunkAnnotations(hunk)}
        gutterEvents={this.createGutterEvents(hunk)}
      />
    );
    if (file._links?.lines) {
      items.push(this.createHunkFooter(expandableHunk));
    }
    return items;
  };

  markConflicts = (hunk: HunkType) => {
    let inConflict = false;
    for (let i = 0; i < hunk.changes.length; ++i) {
      if (hunk.changes[i].content === "<<<<<<< HEAD") {
        inConflict = true;
      }
      if (inConflict) {
        hunk.changes[i].type = "conflict";
      }
      if (hunk.changes[i].content.startsWith(">>>>>>>")) {
        inConflict = false;
      }
    }
  };

  renderFileTitle = (file: File) => {
    if (file.oldPath !== file.newPath && (file.type === "copy" || file.type === "rename")) {
      return (
        <>
          {file.oldPath} <Icon name="arrow-right" color="inherit" /> {file.newPath}
        </>
      );
    } else if (file.type === "delete") {
      return file.oldPath;
    }
    return file.newPath;
  };

  hoverFileTitle = (file: File): string => {
    if (file.oldPath !== file.newPath && (file.type === "copy" || file.type === "rename")) {
      return `${file.oldPath} > ${file.newPath}`;
    } else if (file.type === "delete") {
      return file.oldPath;
    }
    return file.newPath;
  };

  renderChangeTag = (file: File) => {
    const { t } = this.props;
    if (!file.type) {
      return;
    }
    const key = "diff.changes." + file.type;
    let value = t(key);
    if (key === value) {
      value = file.type;
    }
    const color =
      value === "added" ? "success is-outlined" : value === "deleted" ? "danger is-outlined" : "info is-outlined";

    return <ChangeTypeTag className={classNames("is-rounded", "has-text-weight-normal")} color={color} label={value} />;
  };

  hasContent = (file: File) => file && !file.isBinary && file.hunks && file.hunks.length > 0;

  render() {
    const { file, fileControlFactory, fileAnnotationFactory, t } = this.props;
    const { collapsed, sideBySide, diffExpander } = this.state;
    const viewType = sideBySide ? "split" : "unified";

    let body = null;
    let icon = "angle-right";
    if (!collapsed) {
      const fileAnnotations = fileAnnotationFactory ? fileAnnotationFactory(file) : null;
      icon = "angle-down";
      body = (
        <div className="panel-block is-paddingless">
          {fileAnnotations}
          <TokenizedDiffView className={viewType} viewType={viewType} file={file}>
            {(hunks: HunkType[]) => hunks?.map((hunk, n) => this.renderHunk(file, diffExpander.getHunk(n), n))}
          </TokenizedDiffView>
        </div>
      );
    }
    const collapseIcon = this.hasContent(file) ? <Icon name={icon} color="inherit" /> : null;
    const fileControls = fileControlFactory ? fileControlFactory(file, this.setCollapse) : null;
    const sideBySideToggle =
      file.hunks && file.hunks.length > 0 ? (
        <ButtonWrapper className={classNames("level-right", "is-flex")}>
          <ButtonGroup>
            <MenuContext.Consumer>
              {({ setCollapsed }) => (
                <DiffButton
                  icon={sideBySide ? "align-left" : "columns"}
                  tooltip={t(sideBySide ? "diff.combined" : "diff.sideBySide")}
                  onClick={() =>
                    this.toggleSideBySide(() => {
                      if (this.state.sideBySide) {
                        setCollapsed(true);
                      }
                    })
                  }
                />
              )}
            </MenuContext.Consumer>
            {fileControls}
          </ButtonGroup>
        </ButtonWrapper>
      ) : null;

    return (
      <DiffFilePanel className={classNames("panel", "is-size-6")} collapsed={(file && file.isBinary) || collapsed}>
        <div className="panel-heading">
          <FlexWrapLevel className="level">
            <FullWidthTitleHeader
              className={classNames("level-left", "is-flex", "has-cursor-pointer")}
              onClick={this.toggleCollapse}
              title={this.hoverFileTitle(file)}
            >
              {collapseIcon}
              <TitleWrapper className={classNames("is-ellipsis-overflow", "is-size-6")}>
                {this.renderFileTitle(file)}
              </TitleWrapper>
              {this.renderChangeTag(file)}
            </FullWidthTitleHeader>
            {sideBySideToggle}
          </FlexWrapLevel>
        </div>
        {body}
      </DiffFilePanel>
    );
  }
}

export default withTranslation("repos")(DiffFile);
