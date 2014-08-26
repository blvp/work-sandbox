package com.mySampleApplication.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point classes define <code>onModuleLoad()</code>
 */
public class MySampleApplication implements EntryPoint {

    public static final String DEFAULT_BIG_TEXT = "But I must explain to you how all this mistaken idea of denouncing pleasure and praising pain was born and I will give you a complete account of the system, and expound the actual teachings of the great explorer of the truth, the master-builder of human happiness. No one rejects, dislikes, or avoids pleasure itself, because it is pleasure, but because those who do not know how to pursue pleasure rationally encounter consequences that are extremely painful. Nor again is there anyone who loves or pursues or desires to obtain pain of itself, because it is pain, but because occasionally circumstances occur in which toil and pain can procure him some great pleasure. To take a trivial example, which of us ever undertakes laborious physical exercise, except to obtain some advantage from it? But who has any right to find fault with a man who chooses to enjoy a pleasure that has no annoying consequences, or one who avoids a pain that produces no resultant pleasure?";
    private FlexTable flexTable;

    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        generateTable();
        RootPanel.get("anchor").add(flexTable);
    }

    private void generateTable() {
        flexTable = new FlexTable();
        for (int i = 0; i < DEFAULT_DATA().size(); i++) {
            int columnCount = 0;
            ExampleBean bean = DEFAULT_DATA().get(i);
            flexTable.setText(i,columnCount++, bean.getId());
            flexTable.setWidget(i,columnCount++, new ExpandableParagraph(bean));
        }
    }

    private List<? extends ExampleBean> DEFAULT_DATA() {
        List<ExampleBean> res = new ArrayList<ExampleBean>();
        res.add(new ExampleBean("1", "sometext"));
        res.add(new ExampleBean("2", "sometext"));
        res.add(new ExampleBean("3", DEFAULT_BIG_TEXT));
        return res;
    }

    private static class MyAsyncCallback implements AsyncCallback<String> {
        private Label label;

        public MyAsyncCallback(Label label) {
            this.label = label;
        }

        public void onSuccess(String result) {
            label.getElement().setInnerHTML(result);
        }

        public void onFailure(Throwable throwable) {
            label.setText("Failed to receive answer from server!");
        }
    }

    public class ExampleBean implements DescribedInfo {
        private String description;
        private String id;

        public ExampleBean(String s, String sometext) {
            id = s;
            description = sometext;
        }

        public String getDescription() {
            return description;
        }

        public String getId() {
            return id;
        }
    }
}
