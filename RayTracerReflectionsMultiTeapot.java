// javac RayTracerReflectionsMultiTeapot.java
// java RayTracerReflectionsMultiTeapot

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class RayTracerReflectionsMultiTeapot {
    public static void main(String[] args) throws IOException {
        // Load the first teapot
        Scene scene = OBJParser.parse("teapot.obj");
        int originalVertexCount = scene.vertices.size();

        // Load and translate the second teapot
        Scene secondTeapot = OBJParser.parse("teapot.obj");
        translateScene(secondTeapot, new Vector3(2.5, 0, 4.0));

        // Adjust the second teapot's face indices
        adjustFaceIndices(secondTeapot, originalVertexCount);

        // Combine both scenes
        scene.vertices.addAll(secondTeapot.vertices);
        scene.faces.addAll(secondTeapot.faces);

        // Camera setup
        Camera camera = new Camera(new Vector3(0, 2, 6), new Vector3(0, 0, -1), 100);

        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Determine the number of threads (e.g., 8 for 8-core CPU
        int numThreads = Runtime.getRuntime().availableProcessors();
        int rowsPerThread = height / numThreads;

        // List to hold all threads
        ArrayList<Thread> threads = new ArrayList<>();

        // Create and start rendering tasks
        for (int i = 0; i < numThreads; i++) {
            int startY = i * rowsPerThread;
            int endY = (i == numThreads - 1) ? height : startY + rowsPerThread;

            // Create a new thread for this portion of the image
            Thread thread = new Thread(new RenderTask(scene, camera, image, width, height, startY, endY));
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to finish
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Save the final rendered image
        ImageIO.write(image, "png", new File("output.png"));
    }

    private static void adjustFaceIndices(Scene scene, int vertexOffset) {
        for (Face face : scene.faces) {
            for (int i = 0; i < face.vertices.length; i++) {
                face.vertices[i] += vertexOffset; // Offset the indices
            }
        }
    }

    private static void translateScene(Scene scene, Vector3 translation) {
        for (int i = 0; i < scene.vertices.size(); i++) {
            Vector3 v = scene.vertices.get(i);
            Vector3 newV = new Vector3(v.x + translation.x, v.y + translation.y, v.z + translation.z);
            scene.vertices.set(i, newV);
        }
    }
}

class Scene {
    ArrayList<Vector3> vertices = new ArrayList<>();
    ArrayList<Face> faces = new ArrayList<>();
}

class Camera {
    Vector3 position;
    Vector3 lookAt;
    double fieldOfView;

    Camera(Vector3 position, Vector3 lookAt, double fieldOfView) {
        this.position = position;
        this.lookAt = lookAt;
        this.fieldOfView = fieldOfView;
    }
}

class Vector3 {
    double x, y, z;
    Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    Vector3 subtract(Vector3 v) {
        return new Vector3(x - v.x, y - v.y, z - v.z);
    }

    Vector3 cross(Vector3 v) {
        return new Vector3 (
                y * v.z - z * v.y,
                z * v.x - x * v.z,
                x * v.y - y * v.x
            );
    }

    // Multiplies the vector by a scalar
    Vector3 multiply(double scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    double dot (Vector3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    Vector3 normalize() {
        double length = Math.sqrt(x * y + y * y + z * z);
        return new Vector3(x / length, y / length, z / length);
    }
}

class Face {
    int[] vertices;
    Face(int[] vertices) {
        this.vertices = vertices;
    }
}

class RayTracer {
    public static Intersection intersectRayTriangle(Ray ray, Vector3 v0, Vector3 v1, Vector3 v2) {
        final double EPSILON = 0.0000001;
        Vector3 edge1 = v1.subtract(v0);
        Vector3 edge2 = v2.subtract(v0);
        Vector3 h = ray.direction.cross(edge2);
        double a = edge1.dot(h);
        if (a > -EPSILON && a < EPSILON) {
            return new Intersection(false, 0, null, null);
        }

        double f = 1.0 / a;
        Vector3 s = ray.origin.subtract(v0);
        double u = f * s.dot(h);
        if (u < 0.0 || u > 1.0) {
            return new Intersection(false, 0, null, null);
        }

        Vector3 q = s.cross(edge1);
        double v = f * ray.direction.dot(q);
        if (v < 0.0 || u + v > 1.0) {
            return new Intersection(false, 0, null, null);
        }

        double t = f * edge2.dot(q);
        if (t > EPSILON) {
            Vector3 intersectionPoint = new Vector3 (
                    ray.origin.x + ray.direction.x * t,
                    ray.origin.y + ray.direction.y * t,
                    ray.origin.z + ray.direction.z * t
            );
            Vector3 normal = edge1.cross(edge2).normalize();
            return new Intersection(true, t, intersectionPoint, normal);
        }
        else {
            return new Intersection(false, 0, null, null);
        }
    }
}

class Ray {
    Vector3 origin;
    Vector3 direction;

    Ray(Vector3 origin, Vector3 direction) {
        this.origin = origin;
        this.direction = direction;
    }
}

class Intersection {
    boolean hit;
    double distance;
    Vector3 point;
    Vector3 normal;

    Intersection(boolean hit, double distance, Vector3 point, Vector3 normal) {
        this.hit = hit;
        this.distance = distance;
        this.point = point;
        this.normal = normal;
    }
}

class OBJParser {
    public static Scene parse (String filePath) throws IOException {
        Scene scene = new Scene();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ");
                switch (tokens[0]) {
                    case "v":
                        scene.vertices.add(new Vector3(
                                Double.parseDouble(tokens[1]),
                                Double.parseDouble(tokens[2]),
                                Double.parseDouble(tokens[3])
                        ));
                        break;
                    case "f":
                        int[] faceVertices = new int[tokens.length - 1];
                        for (int i = 0; i < faceVertices.length; i++) {
                            faceVertices[i] = Integer.parseInt(tokens[i + 1].split("/")[0]) - 1;
                        }
                        scene.faces.add(new Face(faceVertices))
;
                break;}
            }
        }
        return scene;
    }
}

class RenderTask implements Runnable {
    private Scene scene;
    private Camera camera;
    private BufferedImage image;
    private int width;
    private int height;
    private int startY;
    private int endY;

    public RenderTask(Scene scene, Camera camera, BufferedImage image, int width, int height, int startY, int endY) {
        this.scene = scene;
        this.camera = camera;
        this.image = image;
        this.width = width;
        this.height = height;
        this.startY = startY;
        this.endY = endY;
    }

    @Override
    public void run() {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                double ndcX = (x + 0.5) / width;
                double ndcY = (y + 0.5) / height;
                double screenX = 2 * ndcX - 1;
                double screenY = 1 - 2 * ndcY;
                double aspectRatio = (double) width / height;
                screenX *= aspectRatio;

                Vector3 rayDirection = new Vector3(screenX, screenY, -1).normalize();
                Ray ray = new Ray(camera.position, rayDirection);

                Color color = traceRay(ray, scene, 0); // Recursion depth
                synchronized(image) {
                    image.setRGB(x, y, color.getRGB());
                }
            }
        }
    }

    private static Color traceRay(Ray ray, Scene scene, int depth) {
        if (depth > MAX_REFLECTION_DEPTH) {
            return new Color(0, 0, 0); // return black for maximum recursion depth
        }

        Intersection closestIntersection = null;
        for (Face face : scene.faces) {
            Vector3 v0 = scene.vertices.get(face.vertices[0]);
            Vector3 v1 = scene.vertices.get(face.vertices[1]);
            Vector3 v2 = scene.vertices.get(face.vertices[2]);

            Intersection intersection = RayTracer.intersectRayTriangle(ray, v0, v1, v2);
            if (intersection.hit && (closestIntersection == null || intersection.distance < closestIntersection.distance)) {
                closestIntersection = intersection;
            }
        }

        if (closestIntersection != null) {
            // Calculate reflection
            Vector3 reflectedDirection = reflect(ray.direction, closestIntersection.normal).normalize();
            Ray reflectedRay = new Ray(closestIntersection.point, reflectedDirection);

            // Trace the reflected ray recursively
            Color reflectedColor = traceRay(reflectedRay, scene, depth + 1);

            // Mix original hit color with reflected color (e.g., assume reflective surfaces are white)
            int r = (int) (0.5 * reflectedColor.getRed() + 0.5 * 255); // adjust the factor for blending
            int g = (int) (0.5 * reflectedColor.getGreen() + 0.5 * 255);
            int b = (int) (0.5 * reflectedColor.getBlue() + 0.5 * 255);

            return new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
        } else {
            return new Color(0, 0, 0); // background color
        }
    }

    // Reflect the direction vector using the normal
    private static Vector3 reflect(Vector3 direction, Vector3 normal) {
        double dotProduct = direction.dot(normal);
        return direction.subtract(normal.multiply(2 * dotProduct));
    }

    private static final int MAX_REFLECTION_DEPTH = 3;
}