package rescuenet.gui;

import rescuenet.common.CliArgs;
import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.FleetDirectory;
import rescuenet.common.Mission;
import rescuenet.common.NetworkAddresses;
import rescuenet.drone.DroneServer;
import rescuenet.drone.DroneServiceImpl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class DroneConsoleApp extends Application {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final java.time.Duration STALE_AFTER = java.time.Duration.ofSeconds(6);

    private final Label clock = new Label();
    private final Label linkStatus = new Label("DESLIGADO");
    private final Label registryMeta = new Label();
    private final Label localEndpoint = new Label("—");
    private final Label lastCall = new Label("à espera do heartbeat");
    private final Label stateValue = new Label("—");
    private final Label batteryValue = new Label("—");
    private final ProgressBar batteryBar = new ProgressBar(0);
    private final Label coordsValue = new Label("—");
    private final Label missionValue = new Label("sem missão");
    private final Label status = new Label();
    private final FleetMapPane map = new FleetMapPane();
    private final ObservableList<String> eventRows = FXCollections.observableArrayList();
    private final ListView<String> eventLog = new ListView<>(eventRows);

    private final TextField idField = new TextField();
    private final TextField baseField = new TextField("Base");
    private final TextField latField = new TextField();
    private final TextField lonField = new TextField();
    private final TextField batteryField = new TextField("90");
    private final TextField centralField = new TextField("127.0.0.1:1099");
    private final TextField hostnameField = new TextField();
    private final TextField listenField = new TextField("1101");
    private final Button connectButton = new Button("Ligar à central");
    private final Button disconnectButton = new Button("Desligar nó");

    private volatile boolean running;
    private volatile boolean joined;
    private volatile Instant joinedAt;
    private volatile String centralUrl;
    private volatile FleetDirectory directory;
    private volatile DroneServiceImpl drone;
    private Thread connector;

    private String droneId;
    private String baseName;
    private String hostname;
    private double lat;
    private double lon;
    private double battery;
    private int listen;
    private static String[] bootArgs = new String[0];

    public static void main(String[] args) {
        bootArgs = args;
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        CliArgs cli = CliArgs.parse(bootArgs);
        fillFromCli(cli);
        setLink("DESLIGADO", "link-down");

        centralField.textProperty().addListener((_, _, next) -> centralUrl = next == null ? "" : next.trim());
        disconnectButton.getStyleClass().add("ghost");
        disconnectButton.setDisable(true);
        connectButton.setMaxWidth(Double.MAX_VALUE);
        disconnectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setOnAction(_ -> beginConnection());
        disconnectButton.setOnAction(_ -> shutdown(true));
        batteryBar.setMaxWidth(Double.MAX_VALUE);
        map.setMinHeight(180);
        map.setPrefHeight(220);
        eventLog.getStyleClass().add("alert-log");
        eventLog.setPrefHeight(160);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(buildHeader());
        root.setCenter(buildWorkspace());
        root.setBottom(status);
        status.getStyleClass().add("status-bar");
        status.setText("Indica o IP da central e liga este nó. O heartbeat da outra máquina aparece neste ecrã.");

        Scene scene = new Scene(root, 960, 640);
        scene.getStylesheets().add(getClass().getResource("/rescuenet/gui/command-center.css").toExternalForm());
        stage.setTitle(windowTitle());
        stage.setScene(scene);
        stage.show();

        startClock();
        stage.setOnCloseRequest(_ -> shutdown(true));

        if (!idField.getText().isBlank() && !latField.getText().isBlank() && !lonField.getText().isBlank()) {
            beginConnection();
        }
    }

    private void fillFromCli(CliArgs cli) {
        idField.setText(cli.opt("id", ""));
        baseField.setText(cli.opt("base", "Base"));
        latField.setText(cli.opt("lat", ""));
        lonField.setText(cli.opt("lon", ""));
        batteryField.setText(cli.opt("battery", "90"));
        centralField.setText(cli.opt("central", "127.0.0.1:1099"));
        hostnameField.setText(cli.opt("hostname", NetworkAddresses.localHostName()));
        listenField.setText(Integer.toString(cli.optInt("listen", 1101)));
        centralUrl = centralField.getText().trim();
        registryMeta.setText(DroneServer.directoryUrl(centralUrl));
    }

    private HBox buildHeader() {
        Label wordmark = new Label("RESCUENET");
        wordmark.getStyleClass().add("wordmark");
        Label tagline = new Label("Nó de drone — o servidor chama este processo na LAN.");
        tagline.getStyleClass().add("tagline");
        VBox brand = new VBox(2, wordmark, tagline);

        linkStatus.getStyleClass().add("link-chip");
        clock.getStyleClass().add("clock");
        registryMeta.getStyleClass().add("meta");
        VBox right = new VBox(4, clock, registryMeta, linkStatus);
        right.setAlignment(Pos.TOP_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(16, brand, spacer, right);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildWorkspace() {
        VBox left = new VBox(14, buildConnectionCard(), buildNodeCard());
        left.setPrefWidth(380);
        left.setMinWidth(320);

        VBox right = new VBox(10, sectionTitle("POSIÇÃO"), map, sectionTitle("REGISTO DO NÓ"), eventLog);
        right.getStyleClass().add("panel");
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(eventLog, Priority.ALWAYS);

        HBox workspace = new HBox(18, left, right);
        workspace.getStyleClass().add("workspace");
        return workspace;
    }

    private VBox buildConnectionCard() {
        GridPane form = new GridPane();
        form.getStyleClass().add("form-grid");
        form.add(label("Id"), 0, 0);
        form.add(idField, 1, 0);
        form.add(label("Base"), 0, 1);
        form.add(baseField, 1, 1);
        form.add(label("Latitude"), 0, 2);
        form.add(latField, 1, 2);
        form.add(label("Longitude"), 0, 3);
        form.add(lonField, 1, 3);
        form.add(label("Bateria"), 0, 4);
        form.add(batteryField, 1, 4);
        form.add(label("Central"), 0, 5);
        form.add(centralField, 1, 5);
        form.add(label("Hostname"), 0, 6);
        form.add(hostnameField, 1, 6);
        form.add(label("Listen"), 0, 7);
        form.add(listenField, 1, 7);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(32);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(68);
        form.getColumnConstraints().addAll(c0, c1);
        for (TextField field : List.of(idField, baseField, latField, lonField, batteryField, centralField, hostnameField, listenField)) {
            field.setMaxWidth(Double.MAX_VALUE);
        }

        Label endpointCaption = new Label("Endpoint local");
        endpointCaption.getStyleClass().add("form-label");
        localEndpoint.getStyleClass().add("metric-value");
        Label callCaption = new Label("Última chamada da central");
        callCaption.getStyleClass().add("form-label");
        lastCall.getStyleClass().add("metric-value");

        HBox actions = new HBox(10, connectButton, disconnectButton);
        HBox.setHgrow(connectButton, Priority.ALWAYS);
        HBox.setHgrow(disconnectButton, Priority.ALWAYS);

        Label hint = new Label("Central = IP do servidor:1099. Hostname = IPv4 deste PC. Listen = porta que o servidor chama de volta.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        VBox box = new VBox(12, sectionTitle("LIGAÇÃO LAN"), form, endpointCaption, localEndpoint, callCaption, lastCall, actions, hint);
        box.getStyleClass().add("panel");
        return box;
    }

    private VBox buildNodeCard() {
        stateValue.getStyleClass().add("metric-value");
        batteryValue.getStyleClass().add("metric-value");
        coordsValue.getStyleClass().add("metric-value");
        missionValue.getStyleClass().add("metric-value");
        missionValue.setWrapText(true);
        VBox box = new VBox(
                8,
                sectionTitle("ESTE NÓ"),
                label("Estado"),
                stateValue,
                label("Bateria"),
                batteryBar,
                batteryValue,
                label("Coordenadas"),
                coordsValue,
                label("Missão"),
                missionValue
        );
        box.getStyleClass().add("panel");
        return box;
    }

    private void beginConnection() {
        if (running) {
            return;
        }
        try {
            droneId = idField.getText().trim();
            baseName = baseField.getText().trim();
            hostname = hostnameField.getText().trim();
            centralUrl = centralField.getText().trim();
            lat = Double.parseDouble(latField.getText().trim());
            lon = Double.parseDouble(lonField.getText().trim());
            battery = Double.parseDouble(batteryField.getText().trim());
            listen = Integer.parseInt(listenField.getText().trim());
        } catch (NumberFormatException error) {
            status.setText("Coordenadas, bateria ou porta listen inválidas.");
            return;
        }
        if (droneId.isBlank() || hostname.isBlank() || centralUrl.isBlank()) {
            status.setText("Faltam id, hostname ou endereço da central.");
            return;
        }
        running = true;
        joined = false;
        lockIdentityFields(true);
        centralField.setDisable(false);
        connectButton.setDisable(true);
        disconnectButton.setDisable(false);
        setLink("A LIGAR", "link-wait");
        registryMeta.setText(DroneServer.directoryUrl(centralUrl));
        status.setText("A contactar " + DroneServer.directoryUrl(centralUrl));

        connector = new Thread(this::connectLoop, "drone-connect");
        connector.setDaemon(true);
        connector.start();
    }

    private void connectLoop() {
        while (running) {
            try {
                if (drone == null) {
                    System.setProperty("java.rmi.server.hostname", hostname);
                    drone = new DroneServiceImpl(
                            droneId,
                            hostname,
                            baseName,
                            new Coordinates(lat, lon),
                            battery,
                            listen
                    );
                    Platform.runLater(() -> {
                        if (drone != null) {
                            localEndpoint.setText(drone.endpoint());
                            status.setText("Objeto RMI exportado em " + drone.endpoint());
                        }
                    });
                }
                if (!running) {
                    return;
                }
                if (!joined) {
                    String url = DroneServer.directoryUrl(centralUrl);
                    FleetDirectory dir = DroneServer.lookup(url);
                    if (!running || drone == null) {
                        return;
                    }
                    dir.join(drone.localSnapshot(), drone);
                    directory = dir;
                    joined = true;
                    joinedAt = Instant.now();
                    Platform.runLater(() -> {
                        setLink("REGISTADO", "link-ok");
                        registryMeta.setText(url);
                        centralField.setDisable(true);
                        if (drone != null) {
                            status.setText("Registado. A central chama este nó em " + drone.endpoint());
                        }
                    });
                } else if (centralUnreachable()) {
                    joined = false;
                    Platform.runLater(() -> {
                        setLink("CENTRAL INALCANÇÁVEL", "link-down");
                        centralField.setDisable(false);
                        status.setText("Sem heartbeat da central. A repetir join...");
                    });
                }
            } catch (Exception error) {
                if (!running) {
                    return;
                }
                joined = false;
                Platform.runLater(() -> {
                    setLink("CENTRAL INALCANÇÁVEL", "link-down");
                    centralField.setDisable(false);
                    status.setText("Central inalcançável — a repetir. " + shortError(error));
                });
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean centralUnreachable() {
        if (drone == null || joinedAt == null) {
            return false;
        }
        Instant last = drone.lastRemoteCall();
        Instant now = Instant.now();
        if (last != null) {
            return java.time.Duration.between(last, now).compareTo(STALE_AFTER) > 0;
        }
        return java.time.Duration.between(joinedAt, now).compareTo(STALE_AFTER) > 0;
    }

    private void refresh() {
        clock.setText(LocalTime.now().format(CLOCK));
        DroneServiceImpl node = drone;
        if (node == null) {
            map.update(List.of(), List.of());
            return;
        }
        DroneSnapshot snap = node.localSnapshot();
        stateValue.setText(translateState(snap.state()));
        batteryValue.setText("%.1f%%".formatted(snap.batteryPercent()));
        batteryBar.setProgress(snap.batteryPercent() / 100.0);
        coordsValue.setText("%.4f, %.4f".formatted(snap.location().lat(), snap.location().lon()));
        Mission mission = node.currentMission();
        if (mission == null) {
            missionValue.setText("sem missão");
            map.update(List.of(snap), List.of());
        } else {
            missionValue.setText("%s · %s · %s".formatted(
                    mission.id(),
                    mission.type().name().replace('_', ' '),
                    mission.zoneName()
            ));
            map.update(List.of(snap), List.of(mission));
        }
        Instant remote = node.lastRemoteCall();
        if (remote == null) {
            lastCall.setText("à espera do heartbeat");
        } else {
            lastCall.setText(CLOCK.format(remote.atZone(java.time.ZoneId.systemDefault()).toLocalTime()));
        }
        List<String> events = node.recentEvents();
        eventRows.setAll(events.reversed());
    }

    private void startClock() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(400), _ -> refresh()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        clock.setText(LocalTime.now().format(CLOCK));
    }

    private void shutdown(boolean exit) {
        running = false;
        joined = false;
        if (connector != null) {
            connector.interrupt();
        }
        FleetDirectory dir = directory;
        directory = null;
        String id = droneId;
        DroneServiceImpl node = drone;
        drone = null;
        if (dir != null && id != null) {
            try {
                dir.leave(id);
            } catch (Exception ignored) {
                // central already down
            }
        }
        if (node != null) {
            node.shutdown();
        }
        if (exit) {
            Platform.exit();
            System.exit(0);
        }
    }

    private void lockIdentityFields(boolean locked) {
        idField.setDisable(locked);
        baseField.setDisable(locked);
        latField.setDisable(locked);
        lonField.setDisable(locked);
        batteryField.setDisable(locked);
        hostnameField.setDisable(locked);
        listenField.setDisable(locked);
    }

    private void setLink(String text, String style) {
        linkStatus.setText(text);
        linkStatus.getStyleClass().removeAll("link-ok", "link-wait", "link-down");
        linkStatus.getStyleClass().add(style);
    }

    private String windowTitle() {
        String id = idField.getText().isBlank() ? "drone" : idField.getText().trim();
        return "RescueNet · " + id;
    }

    private static String shortError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 117) + "..." : message;
    }

    private static String translateState(DroneState state) {
        return switch (state) {
            case AVAILABLE -> "DISPONÍVEL";
            case ON_MISSION -> "EM MISSÃO";
            case RETURNING -> "A REGRESSAR";
            case OFFLINE -> "OFFLINE";
        };
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
}
