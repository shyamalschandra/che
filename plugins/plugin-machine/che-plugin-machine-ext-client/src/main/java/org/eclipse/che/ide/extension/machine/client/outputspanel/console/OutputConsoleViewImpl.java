/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.extension.machine.client.outputspanel.console;

import com.google.common.base.Strings;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.PreElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import org.eclipse.che.ide.extension.machine.client.MachineLocalizationConstant;
import org.eclipse.che.ide.extension.machine.client.MachineResources;
import org.eclipse.che.ide.ui.Tooltip;
import static org.eclipse.che.ide.ui.menu.PositionController.HorizontalAlign.MIDDLE;
import static org.eclipse.che.ide.ui.menu.PositionController.VerticalAlign.BOTTOM;
import org.vectomatic.dom.svg.ui.SVGImage;

/**
 * View representation of output console.
 *
 * @author Artem Zatsarynnyi
 * @author Vitaliy Guliy
 */
public class OutputConsoleViewImpl extends Composite implements OutputConsoleView, ScrollHandler {

    interface OutputConsoleViewUiBinder extends UiBinder<Widget, OutputConsoleViewImpl> {
    }

    private static final OutputConsoleViewUiBinder UI_BINDER   = GWT.create(OutputConsoleViewUiBinder.class);

    private ActionDelegate delegate;

    @UiField
    DockLayoutPanel consolePanel;

    @UiField
    FlowPanel       commandPanel;

    @UiField
    FlowPanel       previewPanel;

    @UiField
    Label           commandTitle;

    @UiField
    Label           commandLabel;

    @UiField
    ScrollPanel     scrollPanel;

    @UiField
    FlowPanel       consoleLines;

    @UiField
    Anchor          previewUrlLabel;

    @UiField
    FlowPanel       reRunProcessButton;

    @UiField
    FlowPanel       stopProcessButton;

    @UiField
    FlowPanel       clearOutputsButton;

    @UiField
    FlowPanel       wrapTextButton;

    @UiField
    FlowPanel       scrollToBottomButton;

    /** If true - next printed line should replace the previous one. */
    private boolean carriageReturn;

    /** Follow the output. Scroll to the bottom automatically when <b>true</b>. */
    private boolean followOutput = true;

    /** Scroll to the bottom immediately when view become visible. */
    private boolean followScheduled = false;

    @Inject
    public OutputConsoleViewImpl(MachineResources resources,
                                 MachineLocalizationConstant localization) {
        initWidget(UI_BINDER.createAndBindUi(this));

        reRunProcessButton.add(new SVGImage(resources.reRunIcon()));
        stopProcessButton.add(new SVGImage(resources.stopIcon()));
        clearOutputsButton.add(new SVGImage(resources.clearOutputsIcon()));

        wrapTextButton.add(new SVGImage(resources.lineWrapIcon()));
        scrollToBottomButton.add(new SVGImage(resources.scrollToBottomIcon()));

        scrollPanel.addDomHandler(this, ScrollEvent.getType());

        reRunProcessButton.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (!reRunProcessButton.getElement().hasAttribute("disabled") && delegate != null) {
                    delegate.reRunProcessButtonClicked();
                }
            }
        }, ClickEvent.getType());

        stopProcessButton.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (!stopProcessButton.getElement().hasAttribute("disabled") && delegate != null) {
                    delegate.stopProcessButtonClicked();
                }
            }
        }, ClickEvent.getType());

        clearOutputsButton.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (!clearOutputsButton.getElement().hasAttribute("disabled") && delegate != null) {
                    delegate.clearOutputsButtonClicked();
                }
            }
        }, ClickEvent.getType());

        wrapTextButton.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                if (!wrapTextButton.getElement().hasAttribute("disabled") && delegate != null) {
                    delegate.wrapTextButtonClicked();
                }
            }
        }, ClickEvent.getType());

        scrollToBottomButton.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (!scrollToBottomButton.getElement().hasAttribute("disabled") && delegate != null) {
                    delegate.scrollToBottomButtonClicked();
                }
            }
        }, ClickEvent.getType());

        Tooltip.create((elemental.dom.Element) reRunProcessButton.getElement(),
                BOTTOM,
                MIDDLE,
                localization.consolesReRunButtonTooltip());

        Tooltip.create((elemental.dom.Element) stopProcessButton.getElement(),
                BOTTOM,
                MIDDLE,
                localization.consolesStopButtonTooltip());

        Tooltip.create((elemental.dom.Element) clearOutputsButton.getElement(),
                BOTTOM,
                MIDDLE,
                localization.consolesClearOutputsButtonTooltip());

        Tooltip.create((elemental.dom.Element) wrapTextButton.getElement(),
                BOTTOM,
                MIDDLE,
                localization.consolesWrapTextButtonTooltip());

        Tooltip.create((elemental.dom.Element) scrollToBottomButton.getElement(),
                BOTTOM,
                MIDDLE,
                localization.consolesAutoScrollButtonTooltip());
    }

    @Override
    public void setDelegate(ActionDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void hideCommand() {
        consolePanel.setWidgetHidden(commandPanel, true);
    }

    @Override
    public void hidePreview() {
        consolePanel.setWidgetHidden(previewPanel, true);
    }

    @Override
    public void wrapText(boolean wrap) {
        if (wrap) {
            consoleLines.getElement().setAttribute("wrap", "");
        } else {
            consoleLines.getElement().removeAttribute("wrap");
        }
    }

    @Override
    public void enableAutoScroll(boolean enable) {
        followOutput = enable;
        followOutput();
    }

    @Override
    public void clearConsole() {
        consoleLines.getElement().setInnerHTML("");
    }

    @Override
    public void toggleWrapTextButton(boolean toggle) {
        if (toggle) {
            wrapTextButton.getElement().setAttribute("toggled", "");
        } else {
            wrapTextButton.getElement().removeAttribute("toggled");
        }
    }

    @Override
    public void toggleScrollToEndButton(boolean toggle) {
        if (toggle) {
            scrollToBottomButton.getElement().setAttribute("toggled", "");
        } else {
            scrollToBottomButton.getElement().removeAttribute("toggled");
        }
    }

    @Override
    public void enableStopButton(boolean enable) {
        if (enable) {
            stopProcessButton.getElement().removeAttribute("disabled");
        } else {
            stopProcessButton.getElement().setAttribute("disabled", "");
        }
    }

    @Override
    public void showCommandLine(String commandLine) {
        commandLabel.setText(commandLine);
    }

    @Override
    public void showPreviewUrl(String previewUrl) {
        if (Strings.isNullOrEmpty(previewUrl)) {
            hidePreview();
        } else {
            previewUrlLabel.setText(previewUrl);
            previewUrlLabel.setTitle(previewUrl);
            previewUrlLabel.setHref(previewUrl);
        }
    }

    @Override
    public void print(String text, boolean cr) {
        if (carriageReturn) {
            Node lastChild = consoleLines.getElement().getLastChild();
            if (lastChild != null) {
                lastChild.removeFromParent();
            }
        }

        carriageReturn = cr;

        PreElement pre = DOM.createElement("pre").cast();
        pre.setInnerText(text.isEmpty() ? " " : text);
        consoleLines.getElement().appendChild(pre);

        followOutput();
    }

    @Override
    public void onScroll(ScrollEvent event) {
        // Do nothing if content height less scroll area height
        if (scrollPanel.getElement().getScrollHeight() < scrollPanel.getElement().getOffsetHeight()) {
            followOutput = true;
            if (delegate != null) {
                delegate.onOutputScrolled(followOutput);
            }
            return;
        }

        // Follow output if scroll area is scrolled to the end
        if (scrollPanel.getElement().getScrollTop() + scrollPanel.getElement().getOffsetHeight() > scrollPanel.getElement().getScrollHeight()) {
            followOutput = true;
        } else {
            followOutput = false;
        }

        if (delegate != null) {
            delegate.onOutputScrolled(followOutput);
        }
    }

    /**
     * Scrolls to the bottom if following the output is enabled.
     */
    private void followOutput() {
        if (!followOutput) {
            return;
        }

        /** Scroll bottom immediately if view is visible */
        if (scrollPanel.getElement().getOffsetParent() != null) {
            scrollPanel.scrollToBottom();
            scrollPanel.scrollToLeft();
            return;
        }

        /** Otherwise, check the visibility periodically and scroll the view when it's visible */
        if (!followScheduled) {
            followScheduled = true;

            Scheduler.get().scheduleFixedPeriod(new Scheduler.RepeatingCommand() {
                @Override
                public boolean execute() {
                    if (!followOutput) {
                        followScheduled = false;
                        return false;
                    }

                    if (scrollPanel.getElement().getOffsetParent() != null) {
                        scrollPanel.scrollToBottom();
                        scrollPanel.scrollToLeft();
                        followScheduled = false;
                        return false;
                    }

                    return true;
                }
            }, 500);
        }
    }

}
