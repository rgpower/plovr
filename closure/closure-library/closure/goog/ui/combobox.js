// Copyright 2007 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview A combo box control that allows user input with
 * auto-suggestion from a limited set of options.
 *
*
 * @see ../demos/combobox.html
 */

goog.provide('goog.ui.ComboBox');
goog.provide('goog.ui.ComboBoxItem');

goog.require('goog.Timer');
goog.require('goog.debug.Logger');
goog.require('goog.dom.classes');
goog.require('goog.events');
goog.require('goog.events.InputHandler');
goog.require('goog.events.KeyCodes');
goog.require('goog.events.KeyHandler');
goog.require('goog.string');
goog.require('goog.style');
goog.require('goog.ui.Component');
goog.require('goog.ui.ItemEvent');
goog.require('goog.ui.LabelInput');
goog.require('goog.ui.Menu');
goog.require('goog.ui.MenuItem');
goog.require('goog.ui.registry');
goog.require('goog.userAgent');


/**
 * A ComboBox control.
 * @param {goog.dom.DomHelper=} opt_domHelper Optional DOM helper.
 * @param {goog.ui.Menu=} opt_menu Optional menu.
 * @extends {goog.ui.Component}
 * @constructor
 */
goog.ui.ComboBox = function(opt_domHelper, opt_menu) {
  goog.ui.Component.call(this, opt_domHelper);

  this.labelInput_ = new goog.ui.LabelInput();

  // TODO(user): Allow lazy creation of menus/menu items
  this.menu_ = opt_menu || new goog.ui.Menu(this.getDomHelper());
  this.setupMenu_();
};
goog.inherits(goog.ui.ComboBox, goog.ui.Component);


/**
 * Number of milliseconds to wait before dismissing combobox after blur.
 * @type {number}
 */
goog.ui.ComboBox.BLUR_DISMISS_TIMER_MS = 250;


/**
 * A logger to help debugging of combo box behavior.
 * @type {goog.debug.Logger}
 * @private
 */
goog.ui.ComboBox.prototype.logger_ =
    goog.debug.Logger.getLogger('goog.ui.ComboBox');


/**
 * Keyboard event handler to manage key events dispatched by the input element.
 * @type {goog.events.KeyHandler}
 * @private
 */
goog.ui.ComboBox.prototype.keyHandler_;


/**
 * Input handler to take care of firing events when the user inputs text in
 * the input.
 * @type {goog.events.InputHandler?}
 * @private
 */
goog.ui.ComboBox.prototype.inputHandler_ = null;


/**
 * The last input token.
 * @type {?string}
 * @private
 */
goog.ui.ComboBox.prototype.lastToken_ = null;


/**
 * A LabelInput control that manages the focus/blur state of the input box.
 * @type {goog.ui.LabelInput?}
 * @private
 */
goog.ui.ComboBox.prototype.labelInput_ = null;


/**
 * Drop down menu for the combo box.  Will be created at construction time.
 * @type {goog.ui.Menu?}
 * @private
 */
goog.ui.ComboBox.prototype.menu_ = null;


/**
 * The cached visible count.
 * @type {number}
 * @private
 */
goog.ui.ComboBox.prototype.visibleCount_ = -1;


/**
 * The input element.
 * @type {Element}
 * @private
 */
goog.ui.ComboBox.prototype.input_ = null;


/**
 * The match function.  The first argument for the match function will be
 * a MenuItem's caption and the second will be the token to evaluate.
 * @type {Function}
 * @private
 */
goog.ui.ComboBox.prototype.matchFunction_ = goog.string.startsWith;


/**
 * Element used as the combo boxes button.
 * @type {Element}
 * @private
 */
goog.ui.ComboBox.prototype.button_ = null;


/**
 * Default text content for the input box when it is unchanged and unfocussed.
 * @type {string}
 * @private
 */
goog.ui.ComboBox.prototype.defaultText_ = '';


/**
 * Name for the input box created
 * @type {string}
 * @private
 */
goog.ui.ComboBox.prototype.fieldName_ = '';


/**
 * Timer identifier for delaying the dismissal of the combo menu.
 * @type {?number}
 * @private
 */
goog.ui.ComboBox.prototype.dismissTimer_ = null;


/**
 * True if the unicode inverted triangle should be displayed in the dropdown
 * button. Defaults to false.
 * @type {boolean} useDropdownArrow
 * @private
 */
goog.ui.ComboBox.prototype.useDropdownArrow_ = false;


/**
 * Create the DOM objects needed for the combo box.  A span and text input.
 * @override
 */
goog.ui.ComboBox.prototype.createDom = function() {
  this.input_ = this.getDomHelper().createDom(
      'input', {'name': this.fieldName_, 'autocomplete': 'off'});
  this.button_ = this.getDomHelper().createDom('span',
      goog.getCssName('goog-combobox-button'));
  this.setElementInternal(this.getDomHelper().createDom('span',
      goog.getCssName('goog-combobox'), this.input_, this.button_));
  if (this.useDropdownArrow_) {
    this.button_.innerHTML = '&nbsp;&#x25BC;';
    goog.style.setUnselectable(this.button_, true /* unselectable */);
  }
  this.input_.setAttribute('label', this.defaultText_);
  this.labelInput_.decorate(this.input_);
  this.menu_.setFocusable(false);
  if (!this.menu_.isInDocument()) {
    this.addChild(this.menu_, true);
  }
};


/** @inheritDoc */
goog.ui.ComboBox.prototype.enterDocument = function() {
  goog.ui.ComboBox.superClass_.enterDocument.call(this);

  var handler = this.getHandler();
  handler.listen(this.getElement(),
      goog.events.EventType.MOUSEDOWN, this.onComboMouseDown_);
  handler.listen(this.getDomHelper().getDocument(),
      goog.events.EventType.MOUSEDOWN, this.onDocClicked_);

  handler.listen(this.input_,
      goog.events.EventType.BLUR, this.onInputBlur_);

  this.keyHandler_ = new goog.events.KeyHandler(this.input_);
  handler.listen(this.keyHandler_,
      goog.events.KeyHandler.EventType.KEY, this.handleKeyEvent);

  this.inputHandler_ = new goog.events.InputHandler(this.input_);
  handler.listen(this.inputHandler_,
      goog.events.InputHandler.EventType.INPUT, this.onInputChange_);

  handler.listen(this.menu_,
      goog.ui.Component.EventType.ACTION, this.onMenuSelected_);
};


/** @inheritDoc */
goog.ui.ComboBox.prototype.exitDocument = function() {
  this.keyHandler_.dispose();
  delete this.keyHandler_;
  this.inputHandler_.dispose();
  this.inputHandler_ = null;
  goog.ui.ComboBox.superClass_.exitDocument.call(this);
};


/**
 * Combo box currently can't decorate elements.
 * @return {boolean} The value false.
 */
goog.ui.ComboBox.prototype.canDecorate = function() {
  return false;
};


/** @inheritDoc */
goog.ui.ComboBox.prototype.disposeInternal = function() {
  goog.ui.ComboBox.superClass_.disposeInternal.call(this);

  this.clearDismissTimer_();

  this.labelInput_.dispose();
  this.menu_.dispose();

  this.labelInput_ = null;
  this.menu_ = null;
  this.input_ = null;
  this.button_ = null;
};


/**
 * Dismisses the menu and resets the value of the edit field.
 */
goog.ui.ComboBox.prototype.dismiss = function() {
  this.clearDismissTimer_();
  this.hideMenu_();
  this.menu_.setHighlightedIndex(-1);
};


/**
 * Adds a new menu item at the end of the menu.
 * @param {goog.ui.MenuItem} item Menu item to add to the menu.
 */
goog.ui.ComboBox.prototype.addItem = function(item) {
  this.menu_.addChild(item, true);
};


/**
 * Adds a new menu item at a specific index in the menu.
 * @param {goog.ui.MenuItem} item Menu item to add to the menu.
 * @param {number} n Index at which to insert the menu item.
 */
goog.ui.ComboBox.prototype.addItemAt = function(item, n) {
  this.menu_.addChildAt(item, n, true);
};


/**
 * Removes an item from the menu and disposes it.
 * @param {goog.ui.MenuItem} item The menu item to remove.
 */
goog.ui.ComboBox.prototype.removeItem = function(item) {
  var child = this.menu_.removeChild(item, true);
  if (child) {
    child.dispose();
  }
};


/**
 * Remove all of the items from the ComboBox menu
 */
goog.ui.ComboBox.prototype.removeAllItems = function() {
  for (var i = this.getItemCount() - 1; i >= 0; --i) {
    this.removeItem(this.getItemAt(i));
  }
};


/**
 * Removes a menu item at a given index in the menu.
 * @param {number} n Index of item.
 */
goog.ui.ComboBox.prototype.removeItemAt = function(n) {
  var child = this.menu_.removeChildAt(n, true);
  if (child) {
    child.dispose();
  }
};


/**
 * Returns a reference to the menu item at a given index.
 * @param {number} n Index of menu item.
 * @return {goog.ui.MenuItem?} Reference to the menu item.
 */
goog.ui.ComboBox.prototype.getItemAt = function(n) {
  return /** @type {goog.ui.MenuItem?} */(this.menu_.getChildAt(n));
};


/**
 * Returns the number of items in the list, including non-visible items,
 * such as separators.
 * @return {number} Number of items in the menu for this combobox.
 */
goog.ui.ComboBox.prototype.getItemCount = function() {
  return this.menu_.getChildCount();
};


/**
 * @return {goog.ui.Menu} The menu that pops up.
 */
goog.ui.ComboBox.prototype.getMenu = function() {
  return this.menu_;
};


/**
 * @return {number} The number of visible items in the menu.
 * @private
 */
goog.ui.ComboBox.prototype.getNumberOfVisibleItems_ = function() {
  if (this.visibleCount_ == -1) {
    var count = 0;
    for (var i = 0, n = this.menu_.getChildCount(); i < n; i++) {
      var item = this.menu_.getChildAt(i);
      if (!(item instanceof goog.ui.MenuSeparator) && item.isVisible()) {
        count++;
      }
    }
    this.visibleCount_ = count;
  }

  this.logger_.info('getNumberOfVisibleItems() - ' + this.visibleCount_);
  return this.visibleCount_;
};


/**
 * Sets the match function to be used when filtering the combo box menu.
 * @param {Function} matchFunction The match function to be used when filtering
 *     the combo box menu.
 */
goog.ui.ComboBox.prototype.setMatchFunction = function(matchFunction) {
  this.matchFunction_ = matchFunction;
};


/**
 * @return {Function} The match function for the combox box.
 */
goog.ui.ComboBox.prototype.getMatchFunction = function() {
  return this.matchFunction_;
};


/**
 * Sets the default text for the combo box.
 * @param {string} text The default text for the combo box.
 */
goog.ui.ComboBox.prototype.setDefaultText = function(text) {
  this.defaultText_ = text;
};


/**
 * @return {string} text The default text for the combox box.
 */
goog.ui.ComboBox.prototype.getDefaultText = function() {
  return this.defaultText_;
};


/**
 * Sets the field name for the combo box.
 * @param {string} fieldName The field name for the combo box.
 */
goog.ui.ComboBox.prototype.setFieldName = function(fieldName) {
  this.fieldName_ = fieldName;
};


/**
 * @return {string} The field name for the combo box.
 */
goog.ui.ComboBox.prototype.getFieldName = function() {
  return this.fieldName_;
};


/**
 * Set to true if a unicode inverted triangle should be displayed in the
 * dropdown button.
 * This option defaults to false for backwards compatibility.
 * @param {boolean} useDropdownArrow True to use the dropdown arrow.
 */
goog.ui.ComboBox.prototype.setUseDropdownArrow = function(useDropdownArrow) {
  this.useDropdownArrow_ = !!useDropdownArrow;
};


/**
 * Sets the current value of the combo box.
 * @param {string} value The new value.
 */
goog.ui.ComboBox.prototype.setValue = function(value) {
  this.logger_.info('setValue() - ' + value);
  if (this.labelInput_.getValue() != value) {
    this.labelInput_.setValue(value);
    this.dispatchEvent(goog.ui.Component.EventType.CHANGE);
  }
};


/**
 * @return {string} The current value of the combo box.
 */
goog.ui.ComboBox.prototype.getValue = function() {
  return this.labelInput_.getValue();
};


/**
 * @return {string} The token for the current cursor position in the input box,
 *     when multi-input is disabled it will be the full input value.
 */
goog.ui.ComboBox.prototype.getToken = function() {
  // TODO(user): Implement multi-input such that getToken returns a substring
  // of the whole input delimited by commas.
  return goog.string.htmlEscape(
      goog.string.trim(this.labelInput_.getValue().toLowerCase()));
};


/**
 * @private
 */
goog.ui.ComboBox.prototype.setupMenu_ = function() {
  var sm = this.menu_;
  sm.setVisible(false);
  sm.setAllowAutoFocus(false);
  sm.setAllowHighlightDisabled(true);
};


/**
 * Shows the menu if it isn't already showing.  Also positions the menu
 * correctly, resets the menu item visibilities and highlights the relevent
 * item.
 * @param {boolean} showAll Whether to show all items, with the first matching
 *     item highlighted.
 * @private
 */
goog.ui.ComboBox.prototype.maybeShowMenu_ = function(showAll) {
  var isVisible = this.menu_.isVisible();
  var numVisibleItems = this.getNumberOfVisibleItems_();

  if (isVisible && numVisibleItems == 0) {
    this.logger_.fine('no matching items, hiding');
    this.hideMenu_();

  } else if (!isVisible && numVisibleItems > 0) {
    if (showAll) {
      this.logger_.fine('showing menu');
      this.setItemVisibilityFromToken_('');
      this.setItemHighlightFromToken_(this.getToken());
    }
    // In Safari 2.0, when clicking on the combox box, the blur event is
    // received after the click event that invokes this function. Since we want
    // to cancel the dismissal after the blur event is processed, we have to
    // wait for all event processing to happen.
    goog.Timer.callOnce(this.clearDismissTimer_, 1, this);

    var pos = goog.style.getPageOffset(this.getElement());
    this.menu_.setPosition(pos.x, pos.y + this.getElement().offsetHeight);
    this.showMenu_();
  }
};


/**
 * Show the menu and add an active class to the combo box's element.
 * @private
 */
goog.ui.ComboBox.prototype.showMenu_ = function() {
  this.menu_.setVisible(true);
  goog.dom.classes.add(this.getElement(),
      goog.getCssName('goog-combobox-active'));
};


/**
 * Hide the menu and remove the active class from the combo box's element.
 * @private
 */
goog.ui.ComboBox.prototype.hideMenu_ = function() {
  this.menu_.setVisible(false);
  goog.dom.classes.remove(this.getElement(),
      goog.getCssName('goog-combobox-active'));
};


/**
 * Clears the dismiss timer if it's active.
 * @private
 */
goog.ui.ComboBox.prototype.clearDismissTimer_ = function() {
  if (this.dismissTimer_) {
    goog.Timer.clear(this.dismissTimer_);
    this.dismissTimer_ = null;
  }
};


/**
 * Event handler for when the combo box area has been clicked.
 * @param {goog.events.BrowserEvent} e The browser event.
 * @private
 */
goog.ui.ComboBox.prototype.onComboMouseDown_ = function(e) {
  // We only want this event on the element itself or the input or the button.
  if (e.target == this.getElement() || e.target == this.input_ ||
      goog.dom.contains(this.button_, e.target)) {
    if (this.menu_.isVisible()) {
      this.logger_.fine('Menu is visible, dismissing');
      this.dismiss();
    } else {
      this.logger_.fine('Opening dropdown');
      this.maybeShowMenu_(true);
      if (goog.userAgent.OPERA) {
        // select() doesn't focus <input> elements in Opera.
        this.input_.focus();
      }
      this.input_.select();
      this.menu_.setMouseButtonPressed(true);
      // Stop the click event from stealing focus
      e.preventDefault();
    }
  }
  // Stop the event from propagating outside of the combo box
  e.stopPropagation();
};


/**
 * Event handler for when the document is clicked.
 * @param {goog.events.BrowserEvent} e The browser event.
 * @private
 */
goog.ui.ComboBox.prototype.onDocClicked_ = function(e) {
  if (!goog.dom.contains(this.menu_.getElement(), e.target)) {
    this.logger_.info('onDocClicked_() - dismissing immediately');
    this.dismiss();
  }
};


/**
 * Handle the menu's select event.
 * @param {goog.events.Event} e The event.
 * @private
 */
goog.ui.ComboBox.prototype.onMenuSelected_ = function(e) {
  this.logger_.info('onMenuSelected_()');
  // Stop propagation of the original event and redispatch to allow the menu
  // select to be cancelled at this level. i.e. if a menu item should cause
  // some behavior such as a user prompt instead of assigning the caption as
  // the value.
  if (this.dispatchEvent(new goog.ui.ItemEvent(
      goog.ui.Component.EventType.ACTION, this,
      /** @type {goog.events.EventTarget} */ (e.target)))) {
    var value = e.target.getValue();
    this.logger_.fine('Menu selection: ' + value + '. Dismissing menu');
    this.setValue(goog.string.unescapeEntities(value));
    this.dismiss();
  }
  e.stopPropagation();
};


/**
 * Event handler for when the input box looses focus -- hide the menu
 * @param {goog.events.BrowserEvent} e The browser event.
 * @private
 */
goog.ui.ComboBox.prototype.onInputBlur_ = function(e) {
  this.logger_.info('onInputBlur_() - delayed dismiss');
  this.clearDismissTimer_();
  this.dismissTimer_ = goog.Timer.callOnce(
      this.dismiss, goog.ui.ComboBox.BLUR_DISMISS_TIMER_MS, this);
};


/**
 * Handles keyboard events from the input box.  Returns true if the combo box
 * was able to handle the event, false otherwise.
 * @param {goog.events.KeyEvent} e Key event to handle.
 * @return {boolean} Whether the event was handled by the combo box.
 * @protected
 */
goog.ui.ComboBox.prototype.handleKeyEvent = function(e) {
  var isMenuVisible = this.menu_.isVisible();

  // Give the menu a chance to handle the event.
  if (isMenuVisible && this.menu_.handleKeyEvent(e)) {
    return true;
  }

  // The menu is either hidden or didn't handle the event.
  var handled = false;
  switch (e.keyCode) {
    case goog.events.KeyCodes.ESC:
      // If the menu is visible and the user hit Esc, dismiss the menu.
      if (isMenuVisible) {
        this.logger_.fine('Dismiss on Esc: ' + this.labelInput_.getValue());
        this.dismiss();
        handled = true;
      }
      break;
    case goog.events.KeyCodes.TAB:
      // If the menu is open and an option is highlighted, activate it.
      if (isMenuVisible) {
        var highlighted = this.menu_.getHighlighted();
        if (highlighted) {
          this.logger_.fine('Select on Tab: ' + this.labelInput_.getValue());
          highlighted.performActionInternal(e);
          handled = true;
        }
      }
      break;
    case goog.events.KeyCodes.UP:
    case goog.events.KeyCodes.DOWN:
      // If the menu is hidden and the user hit the up/down arrow, show it.
      if (!isMenuVisible) {
        this.logger_.fine('Up/Down - maybe show menu');
        this.maybeShowMenu_(true);
        handled = true;
      }
      break;
  }

  if (handled) {
    e.preventDefault();
  }

  return handled;
};


/**
 * Handles the content of the input box changing.
 * @param {goog.events.Event} e The INPUT event to handle.
 * @private
 */
goog.ui.ComboBox.prototype.onInputChange_ = function(e) {
  // If the key event is text-modifying, update the menu.
  this.logger_.fine('Key is modifying: ' + this.labelInput_.getValue());
  var token = this.getToken();
  this.setItemVisibilityFromToken_(token);
  this.maybeShowMenu_(false);
  var highlighted = this.menu_.getHighlighted();
  if (token == '' || !highlighted || !highlighted.isVisible()) {
    this.setItemHighlightFromToken_(token);
  }
  this.lastToken_ = token;
  this.dispatchEvent(goog.ui.Component.EventType.CHANGE);
};


/**
 * Loops through all menu items setting their visibility according to a token.
 * @param {string} token The token.
 * @private
 */
goog.ui.ComboBox.prototype.setItemVisibilityFromToken_ = function(token) {
  this.logger_.info('setItemVisibilityFromToken_() - ' + token);
  var isVisibleItem = false;
  var count = 0;
  var recheckHidden = !this.matchFunction_(token, this.lastToken_);

  for (var i = 0, n = this.menu_.getChildCount(); i < n; i++) {
    var item = this.menu_.getChildAt(i);
    if (item instanceof goog.ui.MenuSeparator) {
      // Ensure that separators are only shown if there is at least one visible
      // item before them.
      item.setVisible(isVisibleItem);
      isVisibleItem = false;
    } else if (item instanceof goog.ui.MenuItem) {
      if (!item.isVisible() && !recheckHidden) continue;

      var caption = item.getCaption();
      var visible = this.isItemSticky_(item) ||
          caption && this.matchFunction_(caption.toLowerCase(), token);
      if (typeof item.setFormatFromToken == 'function') {
        item.setFormatFromToken(token);
      }
      item.setVisible(!!visible);
      isVisibleItem = visible || isVisibleItem;

    } else {
      // Assume all other items are correctly using their visibility.
      isVisibleItem = item.isVisible() || isVisibleItem;
    }

    if (!(item instanceof goog.ui.MenuSeparator) && item.isVisible()) {
      count++;
    }
  }

  this.visibleCount_ = count;
};


/**
 * Highlights the first token that matches the given token.
 * @param {string} token The token.
 * @private
 */
goog.ui.ComboBox.prototype.setItemHighlightFromToken_ = function(token) {
  this.logger_.info('setItemHighlightFromToken_() - ' + token);

  if (token == '') {
    this.menu_.setHighlightedIndex(-1);
    return;
  }

  for (var i = 0, n = this.menu_.getChildCount(); i < n; i++) {
    var item = this.menu_.getChildAt(i);
    var caption = item.getCaption();
    if (caption && this.matchFunction_(caption.toLowerCase(), token)) {
      this.menu_.setHighlightedIndex(i);
      if (item.setFormatFromToken) {
        item.setFormatFromToken(token);
      }
      return;
    }
  }
  this.menu_.setHighlightedIndex(-1);
};


/**
 * Returns true if the item has an isSticky method and the method returns true.
 * @param {goog.ui.MenuItem} item The item.
 * @return {boolean} Whether the item has an isSticky method and the method
 *     returns true.
 * @private
 */
goog.ui.ComboBox.prototype.isItemSticky_ = function(item) {
  return typeof item.isSticky == 'function' && item.isSticky();
};



/**
 * Class for combo box items.
 * @param {goog.ui.ControlContent} content Text caption or DOM structure to
 *     display as the content of the item (use to add icons or styling to
 *     menus).
 * @param {Object=} opt_data Identifying data for the menu item.
 * @param {goog.dom.DomHelper=} opt_domHelper Optional dom helper used for dom
 *     interactions.
 * @constructor
 * @extends {goog.ui.MenuItem}
 */
goog.ui.ComboBoxItem = function(content, opt_data, opt_domHelper) {
  goog.ui.MenuItem.call(this, content, opt_data, opt_domHelper);
};
goog.inherits(goog.ui.ComboBoxItem, goog.ui.MenuItem);


// Register a decorator factory function for goog.ui.ComboBoxItems.
goog.ui.registry.setDecoratorByClassName(
    goog.getCssName('goog-combobox-item'), function() {
  // ComboBoxItem defaults to using MenuItemRenderer.
  return new goog.ui.ComboBoxItem(null);
});


/**
 * Whether the menu item is sticky, non-sticky items will be hidden as the
 * user types.
 * @type {boolean}
 * @private
 */
goog.ui.ComboBoxItem.prototype.isSticky_ = false;


/**
 * Sets the menu item to be sticky or not sticky.
 * @param {boolean} sticky Whether the menu item should be sticky.
 */
goog.ui.ComboBoxItem.prototype.setSticky = function(sticky) {
  this.isSticky_ = sticky;
};


/**
 * @return {boolean} Whether the menu item is sticky.
 */
goog.ui.ComboBoxItem.prototype.isSticky = function() {
  return this.isSticky_;
};


/**
 * Sets the format for a menu item based on a token, bolding the token.
 * @param {string} token The token.
 */
goog.ui.ComboBoxItem.prototype.setFormatFromToken = function(token) {
  if (this.isEnabled()) {
    var escapedToken = goog.string.regExpEscape(token);
    var caption = this.getCaption();
    if (caption) {
      var newElement = this.getDomHelper().createElement('span');
      newElement.innerHTML =
          caption.replace(new RegExp(escapedToken, 'i'), function(m) {
            return '<b>' + m + '</b>';
          });
      this.setContent(newElement);
    }
  }
};

