package com.mySampleApplication.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

public class ExpandableParagraph extends Composite {

    public static final int DEFAULT_TEXT_SIZE = 150;
    @UiField
    Button expander;

    @UiField
    Label text;

    private DescribedInfo object;

    boolean expanded;

    interface ExpandableParagraphUiBinder extends UiBinder<VerticalPanel,ExpandableParagraph>{}

    public ExpandableParagraph(DescribedInfo bean) {
        this.object = bean;
        ExpandableParagraphUiBinder binder = GWT.create(ExpandableParagraphUiBinder.class);
        VerticalPanel verticalPanel = binder.createAndBindUi(this);
        initWidget(verticalPanel);
        init();
    }

    private void init() {
        final String fullText = object.getDescription();
        if(fullText.length() <= DEFAULT_TEXT_SIZE){
            text.setText(fullText);
            expander.removeFromParent();
        }else{
            String shortenedText = fullText.substring(0, DEFAULT_TEXT_SIZE);
            text.setText(shortenedText+"...");
            expanded = false;
            expander.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    if(expanded && fullText.length()> DEFAULT_TEXT_SIZE){
                        text.setText(fullText.substring(0, DEFAULT_TEXT_SIZE)+"...");
                        expanded = false;
                        expander.setText("expand");
                    } else if(!expanded) {
                        text.setText(fullText);
                        expanded = true;
                        expander.setText("hide");
                    }
                }
            });
        }
    }
}
