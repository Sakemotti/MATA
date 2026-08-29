import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateFeatureGraphic {
    private static final int WIDTH = 1024;
    private static final int HEIGHT = 500;

    private GenerateFeatureGraphic() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: java fastlane/tools/GenerateFeatureGraphic.java [output.png]");
        }

        Path repositoryRoot = Path.of("").toAbsolutePath().normalize();
        Path outputPath = args.length == 1
            ? repositoryRoot.resolve(args[0]).normalize()
            : repositoryRoot.resolve(
                "fastlane/metadata/android/ja-JP/images/featureGraphic.png"
            );

        if (!outputPath.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Output must remain inside the repository.");
        }

        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            configure(graphics);
            paintBackground(graphics);
            paintCycle(graphics);
            paintRoutineCards(graphics);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(outputPath.getParent());
        if (!ImageIO.write(canvas, "png", outputPath.toFile())) {
            throw new IOException("No PNG writer is available.");
        }
        System.out.printf("Generated %dx%d RGB feature graphic: %s%n", WIDTH, HEIGHT, outputPath);
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void paintBackground(Graphics2D graphics) {
        graphics.setPaint(new LinearGradientPaint(
            0,
            HEIGHT,
            WIDTH,
            0,
            new float[] {0.0f, 0.52f, 1.0f},
            new Color[] {
                new Color(0x00, 0x18, 0x38),
                new Color(0x00, 0x4C, 0x62),
                new Color(0x10, 0x9E, 0x91),
            }
        ));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);

        graphics.setComposite(AlphaComposite.SrcOver.derive(0.16f));
        graphics.setColor(new Color(0x2A, 0xD6, 0xDE));
        graphics.setStroke(new BasicStroke(2.0f));
        for (int inset = -120; inset <= 120; inset += 60) {
            graphics.drawOval(-250 + inset, -190 + inset, 560, 560);
            graphics.drawOval(770 + inset, 130 + inset, 560, 560);
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static void paintCycle(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.72f));
        graphics.setStroke(new BasicStroke(18.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        graphics.setColor(new Color(0x24, 0xC8, 0xDD));
        graphics.drawArc(224, 34, 576, 432, 20, 138);
        graphics.fill(new Polygon(
            new int[] {259, 294, 250},
            new int[] {78, 92, 115},
            3
        ));

        graphics.setColor(new Color(0x83, 0xEE, 0x43));
        graphics.drawArc(224, 34, 576, 432, 202, 138);
        graphics.fill(new Polygon(
            new int[] {765, 730, 774},
            new int[] {422, 408, 385},
            3
        ));

        graphics.setComposite(AlphaComposite.SrcOver.derive(0.14f));
        graphics.setColor(new Color(0xC5, 0xFF, 0x62));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawOval(236, 46, 552, 408);
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static void paintRoutineCards(Graphics2D graphics) {
        int[] yPositions = {92, 206, 320};
        Color[] accents = {
            new Color(0x24, 0xC8, 0xDD),
            new Color(0x5B, 0xE6, 0x77),
            new Color(0xA8, 0xF4, 0x4D),
        };

        for (int index = 0; index < yPositions.length; index++) {
            paintRoutineCard(graphics, 284 + index * 12, yPositions[index], accents[index], index);
        }
    }

    private static void paintRoutineCard(
        Graphics2D graphics,
        int x,
        int y,
        Color accent,
        int index
    ) {
        final int width = 420;
        final int height = 82;

        graphics.setComposite(AlphaComposite.SrcOver.derive(0.14f));
        graphics.setColor(Color.BLACK);
        graphics.fillRoundRect(x + 8, y + 10, width, height, 28, 28);

        graphics.setComposite(AlphaComposite.SrcOver.derive(0.92f));
        graphics.setColor(new Color(0xFA, 0xFF, 0xFF));
        graphics.fillRoundRect(x, y, width, height, 28, 28);

        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(accent);
        graphics.fillOval(x + 24, y + 20, 42, 42);
        graphics.setColor(new Color(0x00, 0x31, 0x4D));
        graphics.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x + 35, y + 41, x + 43, y + 50);
        graphics.drawLine(x + 43, y + 50, x + 57, y + 33);

        graphics.setColor(new Color(0x05, 0x43, 0x5B));
        graphics.fillRoundRect(x + 88, y + 22, 188 - index * 12, 13, 7, 7);
        graphics.setColor(new Color(0x95, 0xB2, 0xB8));
        graphics.fillRoundRect(x + 88, y + 48, 252 - index * 22, 10, 5, 5);

        graphics.setComposite(AlphaComposite.SrcOver.derive(0.35f));
        graphics.setColor(accent);
        graphics.fillRoundRect(x + width - 38, y + 17, 8, 48, 4, 4);
        graphics.setComposite(AlphaComposite.SrcOver);
    }

}
