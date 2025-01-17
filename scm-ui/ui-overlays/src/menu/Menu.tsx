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

import React, {
  AnchorHTMLAttributes,
  ButtonHTMLAttributes,
  ComponentProps,
  createContext,
  FC,
  ReactNode,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react";
import * as RadixMenu from "@radix-ui/react-dropdown-menu";
import styled from "styled-components";
import { DefaultMenuTrigger } from "./MenuTrigger";
import classNames from "classnames";
import { Link as ReactRouterLink, LinkProps as ReactRouterLinkProps } from "react-router-dom";
import Dialog from "../dialog/Dialog";

const MenuContent = styled(RadixMenu.Content)`
  border: var(--scm-border);
  background-color: var(--scm-secondary-background);
  position: relative;
`;

const MenuItem = styled(RadixMenu.Item).attrs({
  className:
    "is-flex is-align-items-center px-3 py-2 has-text-inherit is-clickable is-size-6 has-hover-color-blue is-borderless has-background-transparent has-rounded-border",
})`
  line-height: inherit;
  :focus {
    outline: #af3ee7 3px solid;
    outline-offset: 0px;
  }
  &[data-disabled] {
    color: unset !important;
    opacity: 40%;
    cursor: unset !important;
  }
`;

type MenuLinkProps = Omit<ReactRouterLinkProps, "onSelect"> & Pick<RadixMenu.MenuItemProps, "disabled">;

/**
 * @beta
 * @since 2.44.0
 */
export const MenuLink = React.forwardRef<HTMLAnchorElement, MenuLinkProps>(({ children, disabled, ...props }, ref) => (
  <MenuItem asChild disabled={disabled}>
    <ReactRouterLink ref={ref} {...props}>
      {children}
    </ReactRouterLink>
  </MenuItem>
));

type MenuExternalLinkProps = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "onSelect"> &
  Pick<RadixMenu.MenuItemProps, "disabled">;

/**
 * External links open in a new browser tab with rel flags "noopener" and "noreferrer" set by default.
 *
 * @beta
 * @since 2.44.0
 */
export const MenuExternalLink = React.forwardRef<HTMLAnchorElement, MenuExternalLinkProps>(
  ({ children, disabled, ...props }, ref) => (
    <MenuItem asChild disabled={disabled}>
      <a ref={ref} target="_blank" rel="noopener noreferrer" {...props}>
        {children}
      </a>
    </MenuItem>
  )
);

type MenuButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "onClick" | "onSelect"> &
  Pick<RadixMenu.MenuItemProps, "onSelect">;

/**
 * Use {@link MenuButtonProps#onSelect} to handle menu item selection.
 * The menu will close by default. This behavior can be overwritten by calling {@link Event.preventDefault} on the event.
 *
 * @beta
 * @since 2.44.0
 */
export const MenuButton = React.forwardRef<HTMLButtonElement, MenuButtonProps>(
  ({ children, onSelect, disabled, ...props }, ref) => (
    <MenuItem asChild disabled={disabled} onSelect={onSelect}>
      <button ref={ref} {...props}>
        {children}
      </button>
    </MenuItem>
  )
);

type MenuContextType = {
  handleDialogItemOpenChange: (open: boolean) => void;
};
const MenuContext = createContext<MenuContextType>(null as unknown as MenuContextType);

type MenuDialogProps = Omit<MenuButtonProps, "onSelect"> &
  Omit<ComponentProps<typeof Dialog>, "trigger"> & {
    dialogContent?: ReactNode;
  };

/**
 * @beta
 * @since 2.46.0
 * @see {@link Dialog}
 */
export const MenuDialog = React.forwardRef<HTMLButtonElement, MenuDialogProps>(
  ({ children, dialogContent, title, description, footer }, ref) => {
    const { handleDialogItemOpenChange } = useContext(MenuContext);
    const [open, setOpen] = useState(false);
    const handleSelect = useCallback((event: Event) => event.preventDefault(), []);
    const changeOpen = useCallback(
      (newValue: boolean) => {
        setOpen(newValue);
        handleDialogItemOpenChange(newValue);
      },
      [handleDialogItemOpenChange]
    );

    return (
      <Dialog
        trigger={
          <MenuButton ref={ref} onSelect={handleSelect}>
            {children}
          </MenuButton>
        }
        title={title}
        description={description}
        footer={footer}
        onOpenChange={changeOpen}
        open={open}
      >
        {dialogContent}
      </Dialog>
    );
  }
);

type Props = {
  className?: string;
  trigger?: React.ReactElement;
} & Pick<RadixMenu.DropdownMenuContentProps, "side">;

/**
 * A menu consists of a trigger button (vertical ellipsis icon button by default)
 * and a dropdown container containing individual menu items.
 *
 * @beta
 * @since 2.44.0
 * @see https://www.w3.org/WAI/ARIA/apg/patterns/menubar/
 */
const Menu: FC<Props> = ({ children, side, className, trigger = <DefaultMenuTrigger /> }) => {
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [hasOpenDialog, setHasOpenDialog] = useState(false);
  const handleDialogItemOpenChange = useCallback((open: boolean) => {
    setHasOpenDialog(open);
    if (!open) {
      setDropdownOpen(false);
    }
  }, []);
  const menuContextValue = useMemo<MenuContextType>(
    () => ({
      handleDialogItemOpenChange,
    }),
    [handleDialogItemOpenChange]
  );

  return (
    <RadixMenu.Root open={dropdownOpen} onOpenChange={setDropdownOpen}>
      {trigger}
      <RadixMenu.Portal>
        <MenuContent
          className={classNames(className, "is-flex is-flex-direction-column has-rounded-border has-box-shadow")}
          side={side}
          sideOffset={4}
          collisionPadding={4}
          hidden={hasOpenDialog}
        >
          <MenuContext.Provider value={menuContextValue}>{children}</MenuContext.Provider>
        </MenuContent>
      </RadixMenu.Portal>
    </RadixMenu.Root>
  );
};

export default Menu;
