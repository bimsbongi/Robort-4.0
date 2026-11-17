package robort.quote

import robort.utils.loadResourceFile
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JTextArea

fun findFontSize(targetWidth: Int, g2d: Graphics2D, fontName: String, text: String): Int {
    var fontSize = 5000
    while (fontSize > 10) {
        g2d.font = Font(fontName, Font.PLAIN, fontSize)
        val bounds = g2d.fontMetrics.getStringBounds(text, g2d)

        if (bounds.width <= targetWidth) break

        fontSize = (fontSize * 0.8).toInt()
    }
    return fontSize
}

fun addCenteredTextToImage(baseImage: BufferedImage, topClearance: Double, targetTextWidth: Double, text: String, fontName: String) {
    val g2d = baseImage.createGraphics()
    val fontSize = findFontSize((targetTextWidth * baseImage.width).toInt(), g2d, fontName, text)

    g2d.font = Font(fontName, Font.PLAIN, fontSize)
    val fontMetrics = g2d.fontMetrics
    val bounds = fontMetrics.getStringBounds(text, g2d)

    val x = ((baseImage.width - bounds.width) / 2).toInt()
    val y = (bounds.height + topClearance * baseImage.height).toInt()

    g2d.drawString(text, x, y)
}

fun main() {
    val baseImage = ImageIO.read(loadResourceFile("/Quote/Backgrounds/test.jpg")!!)

    addCenteredTextToImage(baseImage, 0.2, 0.8,"Hello World!", "DejaVu Sans")

    ImageIO.write(baseImage, "jpg", File("./testText.png"))
}