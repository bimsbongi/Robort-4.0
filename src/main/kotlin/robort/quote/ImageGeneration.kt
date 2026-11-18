package robort.quote

import robort.utils.loadResourceFile
import robort.utils.splitTextIntoLines
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

private const val targetMainTextWidth = 0.8
private const val baseAuthorTestWidth = 0.1
private const val maxAuthorTextWidth = 0.35
private const val targetAuthorTextPos = 0.8
private const val targetCharsPerLine = 15
private const val lineGrowthRate = 0.2
private const val authorTextWidthGrowthRate = 0.01

private const val wideFormatCenterHeight = 0.4
private const val tallFormatCenterHeight = 0.3

fun findFontSize(targetWidth: Int, maxHeight: Int, g2d: Graphics2D, fontName: String, text: String): Int {
    var fontSize = 5000
    while (fontSize > 10) {
        g2d.font = Font(fontName, Font.PLAIN, fontSize)
        val bounds = g2d.fontMetrics.getStringBounds(text, g2d)

        if (bounds.width <= targetWidth && bounds.height <= maxHeight) break

        fontSize = (fontSize * 0.8).toInt()
    }
    return fontSize
}

fun addMultilineTextToImage(baseImage: BufferedImage, yMid: Double, xMid: Double, w: Double, hMax: Double, lines: List<String>, fontName: String) : Double {
    val g2d = baseImage.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    val textAreaWidth = (w * baseImage.width).toInt()
    val textAreaHeight = (hMax* baseImage.height / lines.size).toInt()
    val fontSize = lines.minOf { findFontSize(textAreaWidth, textAreaHeight, g2d, fontName, it) }
    g2d.font = Font(fontName, Font.PLAIN, fontSize)

    val totalHeight = lines.size * g2d.fontMetrics.height

    var y = (yMid * baseImage.height - totalHeight / 2 + g2d.fontMetrics.height).toInt()

    for (line in lines) {
        val bounds = g2d.fontMetrics.getStringBounds(line, g2d)

        val x = ((baseImage.width - bounds.width) / 2 + (xMid - 0.5) * baseImage.width).toInt()

        g2d.drawString(line, x, y)
        y += bounds.height.toInt()
    }
    return y / baseImage.height.toDouble()
}

fun placeSingleQuote(baseImage: BufferedImage, yMid: Double, hMax: Double, quote: String, author: String, fontName: String) : Double {
    val quoteLines = splitTextIntoLines(quote, targetCharsPerLine + (lineGrowthRate * quote.length).toInt())
    val authorLines = splitTextIntoLines(author, targetCharsPerLine + (lineGrowthRate * quote.length).toInt())
    val authorWidth = min(baseAuthorTestWidth + authorTextWidthGrowthRate * author.length, maxAuthorTextWidth)

    val yAfterMainQuote = addMultilineTextToImage(baseImage, yMid, 0.5, targetMainTextWidth, hMax, quoteLines, fontName)
    val yFinal = addMultilineTextToImage(baseImage, yAfterMainQuote, targetAuthorTextPos, authorWidth, hMax, authorLines, fontName)

    return yFinal
}

fun placeMultiQuote(baseImage: BufferedImage, yMid: Double, hMax: Double, quotes: List<Pair<String, String>>, fontName: String) {

}

fun main() {
    val baseImage = ImageIO.read(loadResourceFile("/Quote/Backgrounds/test.jpg")!!)

    placeSingleQuote(baseImage, 0.4,0.7, "Hello World, multiline support ist super cool und dieser Text hört einfach nicht auf", "Eike oder so mit extra viel context lol", "DejaVu Sans")

    ImageIO.write(baseImage, "jpg", File("./testText.png"))
}