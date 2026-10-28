package tracewise.ui.gui;

import com.brunomnsilva.smartgraph.graph.Digraph;
import com.brunomnsilva.smartgraph.graph.DigraphEdgeList;
import com.brunomnsilva.smartgraph.graphview.SmartCircularSortedPlacementStrategy;
import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
import com.brunomnsilva.smartgraph.graphview.SmartGraphProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tracewise.domain.Case;
import tracewise.domain.DateRange;
import tracewise.domain.Transaction;
import tracewise.network.AccountNode;
import tracewise.network.FlowEdge;
import tracewise.network.MoneyFlowGraph;

/**
 * F09: the case view's Network tab. Draws who an account sends money to and receives money from, marking alerted
 * accounts and those the case's latest investigation examined. Double-click an account for its transactions, or an
 * edge for the transfers it sums.
 */
class NetworkView {

    /** An edge's element in the drawing: unique per edge, labelled with its total and count. */
    record EdgeLabel(FlowEdge edge) {
        @Override
        public String toString() {
            return Gui.cad(edge.totalCad()) + " (" + edge.transferCount() + ")";
        }
    }

    private final Gui gui;
    private final TextField account = new TextField();
    private final ChoiceBox<Integer> hops = new ChoiceBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final CheckBox autoLayout = new CheckBox("Auto layout");
    private final Label notice = new Label("Select a case");
    private final StackPane canvas = new StackPane();
    private SmartGraphPanel<String, EdgeLabel> panel;
    private String caseId;
    /** The range of the graph on screen, which double-clicks list; the pickers may have changed since. */
    private DateRange drawnRange = DateRange.ALL;

    NetworkView(Gui gui) {
        this.gui = gui;
    }

    Node build() {
        hops.getItems().setAll(1, 2, 3);
        hops.setValue(2);
        account.setPrefColumnCount(12);
        from.setPromptText("from (any)");
        to.setPromptText("to (any)");
        var show = new Button("Show");
        show.getStyleClass().add("primary");
        show.setOnAction(e -> onChangeScope(hops.getValue(), range()));
        account.setOnAction(e -> onChangeScope(hops.getValue(), range()));
        autoLayout.setOnAction(e -> {
            if (panel != null) {
                panel.setAutomaticLayout(autoLayout.isSelected());
            }
        });
        account.setPromptText("account");
        from.setPrefWidth(130);
        to.setPrefWidth(130);
        var bar = new javafx.scene.layout.FlowPane(8, 6, account, new Label("hops"), hops, from, to, show, autoLayout);
        bar.getStyleClass().add("toolbar");
        var legend = new Label("Dark: selected account. Red ring: has an alert. Light blue: examined by the agent. "
                + "Edges: total CAD (number of transfers). Double-click an account or an edge for its transactions.");
        legend.getStyleClass().add("muted");
        legend.setWrapText(true);
        notice.getStyleClass().add("muted");
        var top = new VBox(4, bar, legend, notice);
        canvas.setMinSize(300, 300);
        var pane = new BorderPane(canvas);
        pane.setTop(top);
        pane.setPadding(new Insets(8));
        return pane;
    }

    /** Shows the network of the case's subject account (its opening alert's account). */
    void show(Case c) {
        caseId = c == null ? null : c.id();
        if (c == null) {
            stopLayout();
            canvas.getChildren().clear();
            notice.setText("Select a case");
            return;
        }
        String id = c.id();
        gui.run("Finding the account of " + id, () -> gui.facade.caseSubjectAccount(id), subject -> {
            if (id.equals(caseId)) { // another case may have been selected while this waited
                account.setText(subject);
                onChangeScope(hops.getValue(), range());
            }
        });
    }

    /** The design's onChangeScope: rebuild for the account, hop depth and date range on screen. */
    void onChangeScope(int depth, DateRange range) {
        String id = account.getText().strip();
        if (id.isEmpty()) {
            return;
        }
        String inCase = caseId;
        notice.setText("Building the network of " + id + "...");
        gui.worker.submit("Building the network of " + id, () -> gui.facade.buildNetwork(id, depth, range, inCase),
                graph -> {
                    drawnRange = range;
                    render(graph);
                }, e -> {
                    notice.setText("Could not build the network of " + id + ".");
                    gui.error(e);
                });
    }

    /** Draws the graph; called on the UI thread. */
    void render(MoneyFlowGraph graph) {
        stopLayout();
        Digraph<String, EdgeLabel> g = new DigraphEdgeList<>();
        graph.nodes().forEach(n -> g.insertVertex(n.accountId()));
        graph.edges().forEach(e -> g.insertEdge(e.from(), e.to(), new EdgeLabel(e)));
        panel = new SmartGraphPanel<>(g, properties(), new SmartCircularSortedPlacementStrategy(), stylesheet());
        for (AccountNode n : graph.nodes()) {
            var vertex = panel.getStylableVertex(n.accountId());
            if (n.hop() == 0) {
                vertex.addStyleClass("vertex-root");
            }
            if (n.alerted()) {
                vertex.addStyleClass("vertex-alerted");
            }
            if (n.examined()) {
                vertex.addStyleClass("vertex-examined");
            }
        }
        panel.setVertexDoubleClickAction(v -> showTransactions(v.getUnderlyingVertex().element(), null));
        panel.setEdgeDoubleClickAction(e -> {
            FlowEdge edge = e.getUnderlyingEdge().element().edge();
            showTransactions(edge.from(), edge.to());
        });
        canvas.getChildren().setAll(panel);
        var drawn = panel;
        // the panel can lay out only once it has a size, which a tab not yet shown does not have
        drawn.widthProperty().addListener((o, old, w) -> initOnce(drawn));
        drawn.heightProperty().addListener((o, old, h) -> initOnce(drawn));
        javafx.application.Platform.runLater(() -> initOnce(drawn));
        notice.setText(graph.edges().isEmpty() ? "No transfers in this period."
                : graph.nodes().size() + " accounts, " + graph.edges().size()
                        + (graph.edges().size() == 1 ? " flow" : " flows")
                        + (graph.omittedCount() > 0 ? "; " + graph.omittedCount()
                                + " smaller counterparties omitted (the 50 largest are shown)" : ""));
    }

    /** A replaced panel's automatic layout keeps running on the UI thread unless it is switched off. */
    private void stopLayout() {
        if (panel != null) {
            panel.setAutomaticLayout(false);
        }
    }

    private void initOnce(SmartGraphPanel<String, EdgeLabel> p) {
        if (p == panel && p.getWidth() > 0 && p.getHeight() > 0 && p.getProperties().get("initialised") == null) {
            p.getProperties().put("initialised", true);
            p.init();
            p.setAutomaticLayout(autoLayout.isSelected());
        }
    }

    /** An account's transactions in the range, or only those from one account to another (an edge). */
    private void showTransactions(String accountId, String toAccount) {
        var range = drawnRange;
        gui.run("Loading transactions of " + accountId, () -> gui.facade.transactionsFor(accountId, range).stream()
                .filter(t -> toAccount == null
                        || (t.senderAccountId().equals(accountId) && t.receiverAccountId().equals(toAccount)))
                .toList(), rows -> {
                    var table = new TableView<Transaction>();
                    table.getColumns().setAll(List.of(Gui.column("Transaction", Transaction::id, 120),
                            Gui.column("Time (UTC)", Transaction::timestamp, 130),
                            Gui.column("From", Transaction::senderAccountId, 100),
                            Gui.column("To", Transaction::receiverAccountId, 100),
                            Gui.column("Amount (CAD)", Transaction::amountCad, 110),
                            Gui.column("Channel", Transaction::channel, 80)));
                    table.getItems().setAll(rows);
                    var stage = new Stage();
                    stage.initOwner(gui.stage);
                    stage.setTitle(toAccount == null ? "Transactions of " + accountId
                            : "Transfers from " + accountId + " to " + toAccount);
                    stage.setScene(new javafx.scene.Scene(new BorderPane(table), 700, 400));
                    stage.show();
                });
    }

    private DateRange range() {
        return DateRange.ofDays(from.getValue(), to.getValue());
    }

    private static SmartGraphProperties properties() {
        try (InputStream in = NetworkView.class.getResourceAsStream("/tracewise/smartgraph.properties")) {
            return new SmartGraphProperties(in);
        } catch (IOException e) {
            throw new IllegalStateException("smartgraph.properties is missing from the application", e);
        }
    }

    private static java.net.URI stylesheet() {
        try {
            return NetworkView.class.getResource("/tracewise/smartgraph.css").toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
