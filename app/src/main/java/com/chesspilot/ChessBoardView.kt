package com.chesspilot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.chesspilot.chess.ChessBoardPosition
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A lightweight native board renderer with a best-move arrow overlay. */
class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val lightSquare = 0xFFF0D9B5.toInt()
    private val darkSquare = 0xFFB58863.toInt()
    private val whitePiece = 0xFFFFFBF2.toInt()
    private val blackPiece = 0xFF20242B.toInt()
    private val arrowColor = 0xFF16A085.toInt()

    private val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = arrowColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = arrowColor
        style = Paint.Style.FILL
    }

    private var position: ChessBoardPosition? = null
    private var bestMove: String? = null

    fun setPosition(fen: String, moveUci: String? = null) {
        position = ChessBoardPosition.parse(fen)
        bestMove = moveUci?.takeIf(::isValidUci)
        invalidate()
    }

    fun pieceAtSquare(square: String): Char? = position?.pieceAt(square)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = min(width, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: width)
        setMeasuredDimension(width, desired)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boardSize = min(width.toFloat(), height.toFloat())
        if (boardSize <= 0f) return
        val tile = boardSize / 8f

        for (rank in 0..7) {
            for (file in 0..7) {
                squarePaint.color = if ((rank + file) % 2 == 0) lightSquare else darkSquare
                canvas.drawRect(
                    file * tile,
                    rank * tile,
                    (file + 1) * tile,
                    (rank + 1) * tile,
                    squarePaint
                )
                val piece = position?.pieceAtIndex(rank * 8 + file) ?: continue
                drawPiece(canvas, piece, file, rank, tile)
            }
        }

        bestMove?.let { drawArrow(canvas, it, tile) }
    }

    private fun drawPiece(canvas: Canvas, piece: Char, file: Int, rank: Int, tile: Float) {
        val glyph = when (piece.lowercaseChar()) {
            'k' -> if (piece.isUpperCase()) "♔" else "♚"
            'q' -> if (piece.isUpperCase()) "♕" else "♛"
            'r' -> if (piece.isUpperCase()) "♖" else "♜"
            'b' -> if (piece.isUpperCase()) "♗" else "♝"
            'n' -> if (piece.isUpperCase()) "♘" else "♞"
            'p' -> if (piece.isUpperCase()) "♙" else "♟"
            else -> return
        }
        val x = (file + 0.5f) * tile
        val centerY = (rank + 0.5f) * tile
        piecePaint.textSize = tile * 0.78f
        val baseline = centerY - (piecePaint.ascent() + piecePaint.descent()) / 2f
        val isWhite = piece.isUpperCase()

        outlinePaint.strokeWidth = tile * 0.035f
        outlinePaint.color = if (isWhite) 0xFF333333.toInt() else 0xFFEFE7D8.toInt()
        piecePaint.color = if (isWhite) whitePiece else blackPiece
        piecePaint.style = Paint.Style.STROKE
        piecePaint.strokeWidth = tile * 0.035f
        canvas.drawText(glyph, x, baseline, piecePaint)
        piecePaint.style = Paint.Style.FILL
        canvas.drawText(glyph, x, baseline, piecePaint)
    }

    private fun drawArrow(canvas: Canvas, move: String, tile: Float) {
        val from = squareCenter(move.substring(0, 2), tile) ?: return
        val to = squareCenter(move.substring(2, 4), tile) ?: return
        val dx = to.first - from.first
        val dy = to.second - from.second
        val length = kotlin.math.hypot(dx, dy)
        if (length < 1f) return

        val ux = dx / length
        val uy = dy / length
        val headLength = tile * 0.34f
        val headWidth = tile * 0.23f
        val endX = to.first - ux * tile * 0.12f
        val endY = to.second - uy * tile * 0.12f
        val baseX = endX - ux * headLength
        val baseY = endY - uy * headLength
        val px = -uy
        val py = ux

        arrowPaint.strokeWidth = tile * 0.13f
        canvas.drawLine(from.first, from.second, baseX, baseY, arrowPaint)
        val head = Path().apply {
            moveTo(endX, endY)
            lineTo(baseX + px * headWidth, baseY + py * headWidth)
            lineTo(baseX - px * headWidth, baseY - py * headWidth)
            close()
        }
        canvas.drawPath(head, arrowHeadPaint)
        canvas.drawCircle(from.first, from.second, tile * 0.11f, arrowHeadPaint)
    }

    private fun squareCenter(square: String, tile: Float): Pair<Float, Float>? {
        if (square.length != 2 || square[0] !in 'a'..'h' || square[1] !in '1'..'8') {
            return null
        }
        val file = square[0] - 'a'
        val rankFromBottom = square[1] - '1'
        return Pair((file + 0.5f) * tile, (7 - rankFromBottom + 0.5f) * tile)
    }

    private fun isValidUci(move: String): Boolean =
        Regex("^[a-h][1-8][a-h][1-8][qrbn]?$").matches(move.lowercase())
}
