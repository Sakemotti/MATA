import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateStoreIcon {
    private static final int SOURCE_SIZE = 1024;
    private static final int OUTPUT_SIZE = 512;

    private GenerateStoreIcon() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                "Usage: java fastlane/tools/GenerateStoreIcon.java [output.png]"
            );
        }

        Path repositoryRoot = Path.of("").toAbsolutePath().normalize();
        Path sourceRoot = repositoryRoot.resolve("fastlane/assets/store-icon");
        Path outputPath = args.length == 1
            ? repositoryRoot.resolve(args[0]).normalize()
            : repositoryRoot.resolve("fastlane/metadata/android/ja-JP/images/icon.png");

        if (!outputPath.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Output must remain inside the repository.");
        }

        BufferedImage background = readSource(sourceRoot.resolve("background.png"), false);
        BufferedImage foreground = readSource(sourceRoot.resolve("foreground.png"), true);
        BufferedImage canvas = new BufferedImage(
            OUTPUT_SIZE,
            OUTPUT_SIZE,
            BufferedImage.TYPE_INT_ARGB
        );
        if (!canvas.getColorModel().getColorSpace().isCS_sRGB()) {
            throw new IllegalStateException("Store icon canvas must use the sRGB color space.");
        }
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            );
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(background, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(foreground, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
        } finally {
            graphics.dispose();
        }

        assertFullBleed(canvas);
        Files.createDirectories(outputPath.getParent());
        if (!ImageIO.write(canvas, "png", outputPath.toFile())) {
            throw new IOException("No PNG writer is available.");
        }
        System.out.printf(
            "Generated %dx%d RGBA full-bleed store icon: %s%n",
            OUTPUT_SIZE,
            OUTPUT_SIZE,
            outputPath
        );
    }

    private static BufferedImage readSource(Path path, boolean requiresAlpha) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unsupported image source: " + path);
        }
        if (image.getWidth() != SOURCE_SIZE || image.getHeight() != SOURCE_SIZE) {
            throw new IOException(
                "Icon source must be " + SOURCE_SIZE + "x" + SOURCE_SIZE + ": " + path
            );
        }
        if (requiresAlpha && !image.getColorModel().hasAlpha()) {
            throw new IOException("Foreground source must have an alpha channel: " + path);
        }
        return image;
    }

    private static void assertFullBleed(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha != 0xff) {
                    throw new IllegalStateException(
                        "Store icon must be fully opaque; transparent pixel at " + x + "," + y
                    );
                }
            }
        }
    }
}
