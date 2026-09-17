package rescuenet.gui;

import rescuenet.common.Coordinates;
import rescuenet.common.DroneSnapshot;
import rescuenet.common.DroneState;
import rescuenet.common.Mission;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;

final class FleetMapPane extends Pane {
    private static final double MIN_LAT = -26.05;
    private static final double MAX_LAT = -25.68;
    private static final double MIN_LON = 32.38;
    private static final double MAX_LON = 32.74;

    private final Canvas canvas = new Canvas();
    private List<DroneSnapshot> drones = List.of();
    private List<Mission> missions = List.of();

    FleetMapPane() {
        getStyleClass().add("map-canvas");
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((_, _, _) -> draw());
        heightProperty().addListener((_, _, _) -> draw());
        setMinHeight(220);
        setPrefHeight(280);
    }

    void update(List<DroneSnapshot> drones, List<Mission> missions) {
        this.drones = List.copyOf(drones);
        this.missions = List.copyOf(missions);
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        g.setFill(Color.web("#0d1209"));
        g.fillRect(0, 0, w, h);
        g.setStroke(Color.web("#2a331c"));
        g.setLineWidth(1);
        for (int i = 1; i < 6; i++) {
            g.strokeLine(0, h * i / 6, w, h * i / 6);
            g.strokeLine(w * i / 6, 0, w * i / 6, h);
        }
        g.setFill(Color.web("#8a7f62"));
        g.setFont(Font.font("Segoe UI", 11));
        label(g, "Maputo", new Coordinates(-25.9692, 32.5732), w, h);
        label(g, "Matola", new Coordinates(-25.9622, 32.4589), w, h);
        label(g, "Marracuene", new Coordinates(-25.7369, 32.6744), w, h);

        for (Mission mission : missions) {
            if (!mission.isActive()) {
                continue;
            }
            Point2D p = project(mission.destination(), w, h);
            g.setStroke(Color.web("#e2b13c"));
            g.setLineDashes(4);
            g.strokeOval(p.getX() - 10, p.getY() - 10, 20, 20);
            g.setLineDashes();
            g.setFill(Color.web("#e2b13c"));
            g.fillText(mission.zoneName(), p.getX() + 12, p.getY() - 4);
        }

        for (DroneSnapshot drone : drones) {
            Point2D p = project(drone.location(), w, h);
            Color color = switch (drone.state()) {
                case AVAILABLE -> Color.web("#8fd18a");
                case ON_MISSION -> Color.web("#e2b13c");
                case RETURNING -> Color.web("#8ebdd4");
                case OFFLINE -> Color.web("#e0544a");
            };
            g.setFill(color);
            g.fillOval(p.getX() - 5, p.getY() - 5, 10, 10);
            g.setFill(Color.web("#f3ead0"));
            g.fillText(drone.id(), p.getX() + 8, p.getY() + 4);
        }
    }

    private void label(GraphicsContext g, String name, Coordinates at, double w, double h) {
        Point2D p = project(at, w, h);
        g.fillText(name, p.getX() - 18, p.getY() + 16);
    }

    private static Point2D project(Coordinates c, double w, double h) {
        double x = (c.lon() - MIN_LON) / (MAX_LON - MIN_LON) * w;
        double y = (1 - (c.lat() - MIN_LAT) / (MAX_LAT - MIN_LAT)) * h;
        return new Point2D(x, y);
    }
}
