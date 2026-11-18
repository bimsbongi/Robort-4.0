package robort.quote

import robort.utils.loadResourceFile
import robort.utils.splitTextIntoLines
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

private const val targetMainTextWidth = 0.8
private const val baseAuthorTestWidth = 0.1
private const val maxAuthorTextWidth = 0.38
private const val targetAuthorTextPos = 0.8

private const val mainTextTargetChars = 15
private const val mainTextLineGrowthRate = 0.3

private const val authorTextTargetChars = 7
private const val authorTextLineGrowthRate = 0.3
private const val authorTextWidthGrowthRate = 0.02

private const val quoteTextFraction = 0.7
private const val authorTextVerticalOffset = 0.65
private const val authorTextHeight = 0.25

private const val wideFormatCenterHeight = 0.4
private const val tallFormatCenterHeight = 0.3

private const val shadowBlurStrength = 0.035
private const val shadowTransparency = 0.6

private enum class KernelOrientation {
    HORIZONTAL, VERTICAL
}

private fun get1dGaussianBlurFilter(radius: Int, orientation: KernelOrientation): ConvolveOp {
    if (radius < 1) {
        throw IllegalArgumentException("Radius must be >= 1")
    }

    val size = radius * 2 + 1
    val data = FloatArray(size)

    val sigma = radius / 3.0f
    val twoSigmaSquare = 2.0f * sigma * sigma
    val sigmaRoot = (sqrt(twoSigmaSquare * Math.PI)).toFloat()
    var total = 0.0f

    for (x in -radius..radius) {
        val distance = (x * x).toFloat()
        val value = exp((-distance / twoSigmaSquare).toDouble()).toFloat() / sigmaRoot
        data[x + radius] = value
        total += value
    }


    // Normalize the kernel so it doesn't brighten or darken the image
    for (i in data.indices) {
        data[i] /= total
    }

    if (orientation == KernelOrientation.VERTICAL) {
        return ConvolveOp(Kernel(1, size, data), ConvolveOp.EDGE_NO_OP, null)
    } else {
        return ConvolveOp(Kernel(size, 1, data), ConvolveOp.EDGE_NO_OP, null)
    }
}

private fun makeImageTranslucent(image: BufferedImage, factor: Double) {
    val width = image.getWidth()
    val height = image.getHeight()
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    val resultPixels = IntArray(pixels.size)

    for (i in pixels.indices) {
        var alpha = (pixels[i] shr 24) and 0xff
        val red = (pixels[i] shr 16) and 0xff
        val green = (pixels[i] shr 8) and 0xff
        val blue = pixels[i] and 0xff

        alpha = (alpha * factor).toInt()
        resultPixels[i] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue;
    }

    image.setRGB(0, 0, width, height, resultPixels, 0, width);
}

private fun findFontSize(targetWidth: Int, maxHeight: Int, g2d: Graphics2D, fontName: String, text: String): Int {
    var fontSize = 5000
    while (fontSize > 10) {
        g2d.font = Font(fontName, Font.PLAIN, fontSize)
        val bounds = g2d.fontMetrics.getStringBounds(text, g2d)

        if (bounds.width <= targetWidth && bounds.height <= maxHeight) break

        fontSize = (fontSize * 0.8).toInt()
    }
    return fontSize
}

private fun addMultilineTextToImage(baseImage: BufferedImage, yMid: Double, xMid: Double, w: Double, hMax: Double, lines: List<String>, fontName: String, color: Color) {
    val shadowImage = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)
    val intermediateImage = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)
    val stencilImage = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)

    val shadowGraphics = shadowImage.createGraphics()
    shadowGraphics.color = Color.BLACK

    val textAreaWidth = (w * baseImage.width).toInt()
    val textAreaHeight = (hMax* baseImage.height / lines.size).toInt()
    val fontSize = lines.minOf { findFontSize(textAreaWidth, textAreaHeight, shadowGraphics, fontName, it) }
    shadowGraphics.font = Font(fontName, Font.PLAIN, fontSize)

    val totalHeight = lines.size * shadowGraphics.fontMetrics.height

    var y = (yMid * baseImage.height - totalHeight / 2 + shadowGraphics.fontMetrics.height).toInt()
    for (line in lines) {
        val bounds = shadowGraphics.fontMetrics.getStringBounds(line, shadowGraphics)

        val x = ((baseImage.width - bounds.width) / 2 + (xMid - 0.5) * baseImage.width).toInt()

        shadowGraphics.drawString(line, x, y)
        y += bounds.height.toInt()
    }
    shadowGraphics.dispose()

    makeImageTranslucent(shadowImage, shadowTransparency)

    get1dGaussianBlurFilter((shadowBlurStrength * baseImage.height).toInt(), KernelOrientation.VERTICAL).filter(shadowImage, intermediateImage)
    get1dGaussianBlurFilter((shadowBlurStrength * baseImage.height).toInt(), KernelOrientation.HORIZONTAL).filter(intermediateImage, stencilImage)

    val textGraphics = stencilImage.createGraphics()
    textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    textGraphics.color = color
    textGraphics.font = Font(fontName, Font.PLAIN, fontSize)

    y = (yMid * baseImage.height - totalHeight / 2 + shadowGraphics.fontMetrics.height).toInt()
    for (line in lines) {
        val bounds = textGraphics.fontMetrics.getStringBounds(line, textGraphics)

        val x = ((baseImage.width - bounds.width) / 2 + (xMid - 0.5) * baseImage.width).toInt()

        textGraphics.drawString(line, x, y)
        y += bounds.height.toInt()
    }
    textGraphics.dispose()

    baseImage.createGraphics().drawImage(stencilImage, 0, 0, null)
}

private fun placeSingleQuote(baseImage: BufferedImage, yMid: Double, hMax: Double, quote: String, author: String, fontName: String, color: Color) {
    val quoteLines = splitTextIntoLines(quote, mainTextTargetChars + (mainTextLineGrowthRate * quote.length).toInt())
    val authorLines = splitTextIntoLines(author, authorTextTargetChars + (authorTextLineGrowthRate * quote.length).toInt())
    val authorWidth = min(baseAuthorTestWidth + authorTextWidthGrowthRate * author.length, maxAuthorTextWidth)

    addMultilineTextToImage(baseImage, yMid, 0.5, targetMainTextWidth, hMax, quoteLines, fontName, color)
    addMultilineTextToImage(baseImage, yMid + hMax * authorTextVerticalOffset, targetAuthorTextPos, authorWidth, hMax * authorTextHeight, authorLines, fontName, color)
}

private fun placeMultiQuote(baseImage: BufferedImage, hMax: Double, quotes: List<Pair<String, String>>, fontName: String, color: Color) {
    val hMaxEach = hMax / quotes.size
    val yMid = if (baseImage.width > baseImage.height) wideFormatCenterHeight else tallFormatCenterHeight

    quotes.forEachIndexed { index, (quote, author) ->
        val currentYMid = yMid - hMax / 2 + hMaxEach / 2 + hMaxEach * index
        placeSingleQuote(baseImage, currentYMid, hMaxEach * quoteTextFraction, quote, author, fontName, color)
    }
}

fun main() {
    val baseImage = ImageIO.read(loadResourceFile("/Quote/Backgrounds/test.jpg")!!)

    val quotes = listOf(
        "Was ist das für ne weirde Gruppe an Menschen in dem Loch?" to "Julian übers Publikum bei den LOL Finals"
    )

    placeMultiQuote(baseImage, 0.8, quotes, "Comfortaa, Bold", Color.WHITE)

    ImageIO.write(baseImage, "jpg", File("./testText.png"))
}