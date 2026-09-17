package rescuenet.gui;

import rescuenet.central.EmergencyListener;
import rescuenet.central.EmergencyServer;
import rescuenet.common.CliArgs;
import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.Mission;
import rescuenet.common.MissionType;
import rescuenet.common.NetworkAddresses;
import rescuenet.common.Priority;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CommandCenterApp extends Application {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static EmergencyServer bootServer;

    private EmergencyServer server;
    private final ObservableList<DroneSnapshot> fleetRows = FXCollections.observableArrayList();
    private final ObservableList<Mission> missionRows = FXCollections.observableArrayList();
    private final ObservableList<String> alertRows = FXCollections.observableArrayList();
    private final TableView<DroneSnapshot> fleetTable = new TableView<>(fleetRows);
    private final TableView<Mission> missionTable = new TableView<>(missionRows);
    private final ListView<String> alertLog = new ListView<>(alertRows);
    private final FleetMapPane map = new FleetMapPane();
    private final VBox failoverBanner = new VBox();
    private final Label failoverBody = new Label();
    private final Label status = new Label();
    private final Label clock = new Label();
    private final Label registryMeta = new Label();

    public static void main(String[] args) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        int port = cli.optInt("port", 1099);
        bootServer = EmergencyServer.start(port);
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        server = bootServer;
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(new VBox(buildHeader(), buildFailover()));
        root.setCenter(buildWorkspace());
        root.setBottom(status);
        status.getStyleClass().add("status-bar");
        status.setText("À espera de drones na LAN. Quando um nó falhar durante uma missão, a Central reatribui sozinha.");

        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/rescuenet/gui/command-center.css").toExternalForm());
        stage.setTitle("RescueNet Command Center");
        stage.setScene(scene);
        stage.show();

        server.addListener(new UiListener());
        refresh();
        startClock();
        registryMeta.setText("rmi://%s:%d/%s".formatted(
                NetworkAddresses.localHostName(),
                server.port(),
                "RescueNet"
        ));
        stage.setOnCloseRequest(_ -> {
            server.stop();
            Platform.exit();
            System.exit(0);
        });
    }

    private HBox buildHeader() {
        Label wordmark = new Label("RESCUENET");
        wordmark.getStyleClass().add("wordmark");
        Label tagline = new Label("Quando um drone falha, a missão não pode falhar.");
        tagline.getStyleClass().add("tagline");
        VBox brand = new VBox(2, wordmark, tagline);

        registryMeta.getStyleClass().add("meta");
        clock.getStyleClass().add("clock");
        VBox right = new VBox(4, clock, registryMeta);
        right.setAlignment(Pos.TOP_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox header = new HBox(16, brand, spacer, right);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildFailover() {
        Label title = new Label("REATRIBUIÇÃO AUTOMÁTICA");
        title.getStyleClass().add("failover-title");
        failoverBody.getStyleClass().add("failover-body");
        failoverBanner.getStyleClass().add("failover");
        failoverBanner.getChildren().addAll(title, failoverBody);
        failoverBanner.setVisible(false);
        failoverBanner.setManaged(false);
        return failoverBanner;
    }

    private HBox buildWorkspace() {
        VBox fleet = new VBox(10, sectionTitle("FROTA DE DRONES"), fleetTable, map);
        fleet.getStyleClass().add("panel");
        HBox.setHgrow(fleet, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(fleetTable, javafx.scene.layout.Priority.ALWAYS);
        configureFleetTable();

        VBox dispatch = buildDispatch();
        dispatch.setPrefWidth(340);
        dispatch.setMinWidth(300);

        VBox missions = new VBox(10, sectionTitle("MISSÕES"), missionTable, sectionTitle("REGISTO"), alertLog);
        missions.getStyleClass().add("panel");
        HBox.setHgrow(missions, javafx.scene.layout.Priority.ALWAYS);
        configureMissionTable();
        alertLog.getStyleClass().add("alert-log");
        alertLog.setPrefHeight(140);

        VBox right = new VBox(14, dispatch, missions);
        HBox.setHgrow(right, javafx.scene.layout.Priority.ALWAYS);

        HBox workspace = new HBox(18, fleet, right);
        workspace.getStyleClass().add("workspace");
        return workspace;
    }

    private VBox buildDispatch() {
        ComboBox<MissionType> type = new ComboBox<>(FXCollections.observableArrayList(MissionType.values()));
        type.setValue(MissionType.BUSCA_E_SALVAMENTO);
        ComboBox<KnownZone> zone = new ComboBox<>(FXCollections.observableArrayList(KnownZone.values()));
        zone.setValue(KnownZone.ZONA_A);
        ComboBox<Priority> priority = new ComboBox<>(FXCollections.observableArrayList(Priority.values()));
        priority.setValue(Priority.HIGH);
        TextField lat = new TextField();
        TextField lon = new TextField();
        zone.valueProperty().addListener((_, _, next) -> {
            if (next != null) {
                lat.setText(Double.toString(next.coordinates.lat()));
                lon.setText(Double.toString(next.coordinates.lon()));
            }
        });
        lat.setText(Double.toString(KnownZone.ZONA_A.coordinates.lat()));
        lon.setText(Double.toString(KnownZone.ZONA_A.coordinates.lon()));

        Button dispatch = new Button("Criar ocorrência e alocar");
        dispatch.setMaxWidth(Double.MAX_VALUE);
        dispatch.setOnAction(_ -> {
            dispatch.setDisable(true);
            KnownZone selected = zone.getValue();
            Coordinates dest = new Coordinates(Double.parseDouble(lat.getText()), Double.parseDouble(lon.getText()));
            Thread worker = new Thread(() -> {
                try {
                    server.createEmergency(type.getValue(), selected.label, dest, priority.getValue());
                } catch (Exception error) {
                    Platform.runLater(() -> status.setText("Falha ao criar ocorrência: " + error.getMessage()));
                } finally {
                    Platform.runLater(() -> {
                        dispatch.setDisable(false);
                        refresh();
                    });
                }
            }, "dispatch");
            worker.setDaemon(true);
            worker.start();
        });

        GridPane form = new GridPane();
        form.getStyleClass().add("form-grid");
        form.add(label("Tipo"), 0, 0);
        form.add(type, 1, 0);
        form.add(label("Zona"), 0, 1);
        form.add(zone, 1, 1);
        form.add(label("Prioridade"), 0, 2);
        form.add(priority, 1, 2);
        form.add(label("Latitude"), 0, 3);
        form.add(lat, 1, 3);
        form.add(label("Longitude"), 0, 4);
        form.add(lon, 1, 4);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(34);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(66);
        form.getColumnConstraints().addAll(c0, c1);
        type.setMaxWidth(Double.MAX_VALUE);
        zone.setMaxWidth(Double.MAX_VALUE);
        priority.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("O Central escolhe o drone ONLINE/AVAILABLE com melhor bateria e menor distância euclidiana.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        VBox box = new VBox(12, sectionTitle("NOVA OCORRÊNCIA"), form, dispatch, hint);
        box.getStyleClass().add("panel");
        return box;
    }

    private void configureFleetTable() {
        fleetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        fleetTable.setPlaceholder(new Label("Nenhum drone registado. Suba DR-001, DR-002 e DR-003."));
        fleetTable.getColumns().addAll(
                col("ID", drone -> drone.id(), 80),
                col("IP", drone -> drone.ip(), 110),
                statusCol(),
                col("Bateria", drone -> "%.0f%%".formatted(drone.batteryPercent()), 80),
                col("Estado", drone -> translateState(drone.state()), 120),
                col("Base", DroneSnapshot::baseName, 110),
                col("Missão", drone -> drone.missionId() == null ? "—" : drone.missionId(), 80)
        );
        fleetTable.setPrefHeight(240);
    }

    private void configureMissionTable() {
        missionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        missionTable.setPlaceholder(new Label("Sem missões. Crie uma ocorrência à direita."));
        missionTable.getColumns().addAll(
                mcol("ID", Mission::id, 80),
                mcol("Alvo", Mission::zoneName, 120),
                mcol("Prioridade", mission -> mission.priority().name(), 90),
                mcol("Drone", mission -> mission.assignedDroneId() == null ? "—" : mission.assignedDroneId(), 80),
                mcol("Estado", mission -> mission.state().name(), 110),
                mcol("Evento", Mission::lastEvent, 240)
        );
        missionTable.setPrefHeight(180);
    }

    private TableColumn<DroneSnapshot, String> statusCol() {
        TableColumn<DroneSnapshot, String> column = col("Link", drone -> drone.state() == DroneState.OFFLINE ? "OFFLINE" : "ONLINE", 90);
        column.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("badge-online", "badge-offline");
                if (!empty && item != null) {
                    getStyleClass().add("OFFLINE".equals(item) ? "badge-offline" : "badge-online");
                }
            }
        });
        return column;
    }

    private static TableColumn<DroneSnapshot, String> col(String title, java.util.function.Function<DroneSnapshot, String> getter, double width) {
        TableColumn<DroneSnapshot, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<Mission, String> mcol(String title, java.util.function.Function<Mission, String> getter, double width) {
        TableColumn<Mission, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-title");
        return label;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private static String translateState(DroneState state) {
        return switch (state) {
            case AVAILABLE -> "DISPONÍVEL";
            case ON_MISSION -> "EM MISSÃO";
            case RETURNING -> "A REGRESSAR";
            case OFFLINE -> "OFFLINE";
        };
    }

    private void refresh() {
        List<DroneSnapshot> fleet = server.fleetSnapshots();
        List<Mission> missions = server.missions();
        fleetRows.setAll(fleet);
        missionRows.setAll(missions);
        alertRows.setAll(server.alerts().reversed());
        map.update(fleet, missions);
        long online = fleet.stream().filter(drone -> drone.state() != DroneState.OFFLINE).count();
        status.setText("Frota %d online / %d total  ·  missões activas %d".formatted(
                online,
                fleet.size(),
                missions.stream().filter(Mission::isActive).count()
        ));
    }

    private void showFailover(String text) {
        failoverBody.setText(text);
        failoverBanner.setVisible(true);
        failoverBanner.setManaged(true);
        Timeline hide = new Timeline(new KeyFrame(Duration.seconds(12), _ -> {
            failoverBanner.setVisible(false);
            failoverBanner.setManaged(false);
        }));
        hide.play();
    }

    private void startClock() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            clock.setText(LocalTime.now().format(CLOCK));
            refresh();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        clock.setText(LocalTime.now().format(CLOCK));
    }

    private final class UiListener implements EmergencyListener {
        @Override
        public void onFleetUpdated() {
            Platform.runLater(CommandCenterApp.this::refresh);
        }

        @Override
        public void onMissionUpdated(Mission mission) {
            Platform.runLater(CommandCenterApp.this::refresh);
        }

        @Override
        public void onFailover(Mission mission, String failedDroneId, String newDroneId) {
            Platform.runLater(() -> showFailover(
                    "%s caiu. %s foi reatribuída a %s.".formatted(failedDroneId, mission.id(), newDroneId)
            ));
        }

        @Override
        public void onAlert(String message) {
            Platform.runLater(CommandCenterApp.this::refresh);
        }
    }

    private enum KnownZone {
        ZONA_A("Zona A", new Coordinates(-25.9300, 32.5600)),
        ZONA_B("Zona B", new Coordinates(-25.8800, 32.6100)),
        COSTA_MATOLA("Costa da Matola", new Coordinates(-25.9550, 32.4300)),
        NORTE_MARRACUENE("Norte de Marracuene", new Coordinates(-25.7200, 32.6900));

        final String label;
        final Coordinates coordinates;

        KnownZone(String label, Coordinates coordinates) {
            this.label = label;
            this.coordinates = coordinates;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
