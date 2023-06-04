package cz.uhk.diplomovaprace.PianoRoll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Canvas.VertexMode
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.AttributeSet
import android.view.*
import android.view.GestureDetector.OnGestureListener
import cz.uhk.diplomovaprace.PianoRoll.Midi.MidiCreator
import cz.uhk.diplomovaprace.PianoRoll.Midi.MidiFactory
import cz.uhk.diplomovaprace.PianoRoll.Midi.MidiPlayer
import cz.uhk.diplomovaprace.PianoRoll.Midi.Track
import cz.uhk.diplomovaprace.PianoRoll.Note
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*


class PianoRollView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs),
    SurfaceHolder.Callback, OnGestureListener, ScaleGestureDetector.OnScaleGestureListener {

    private var paint = Paint()
    private var notes = ArrayList<Note>()
    private var selectedNotes = ArrayList<Note>()
    private var playingNotes = ArrayList<Note>()
    private var pianoKeys = ArrayList<RectF>()  // TODO: vyuzit tento ArrayList
    private var playingPianoKeys = ArrayList<Int>() // onTap only
    private var buttons = ArrayList<RectF>() // 0: play; 1: record; 2: stop;
    // TODO: vsechno dat do ArrayList()<RectF> -> pohlidam si tim klikani, vim, kde co je

    private var drawThread: DrawThread? = null
    private val gestureDetector = GestureDetector(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)

    private var scrollX = 0f
    private var scrollY = 0f
    private var scaleFactorX = 1f
    private var scaleFactorY = 1f
    private var scaling = false
    private var scalingX = false
    private var scalingY = false
    private var startedSpanX = 0f
    private var startedSpanY = 0f
    private var widthDifference = 0f
    private var heightDifference = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var timelineHeight = height / 20f
    private var pianoKeyWidth = width / 16f
    private var pianoKeyHeight = (height - timelineHeight) / 128f

    private var barTimeSignature = 4 / 4f
    private var beatLength = 480
    private var barLength = barTimeSignature * 4 * beatLength
    private var tempo = 60

    private var isPlaying = false
    private var isRecording = false                 // TODO: integrate recording function
    private var lineOnTime = 0f
    private var movingTimeLine = false
    private var elapsedTime = System.currentTimeMillis()
    private var lastFrameTime = System.currentTimeMillis()
    private var currentTime = System.currentTimeMillis()
    private var midiPlayer = MidiPlayer()

    init {
        paint.color = Color.YELLOW
        paint.style = Paint.Style.FILL
        paint.hinting = Paint.HINTING_OFF
        holder.addCallback(this)
        setWillNotDraw(false)
    }

    private inner class DrawThread() : Thread() {
        override fun run() {
            while (isPlaying) {
                invalidate()
                // sleep(50) // kontrolovat FPS
            }
        }

        fun stopDrawing() {
            isPlaying = false
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
        if (!isPlaying) {
            redrawAll()
        }

        return true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Inicialiazce promennych
        // Promenne pro prvotni zobrazeni



        scrollX = 0f
        scrollY = 0f
        scaleFactorX = 1f
        scaleFactorY = 1f

        // Ostatni promenne
        scaling = false
        widthDifference = width - (width / scaleFactorX)
        heightDifference = height - (height / scaleFactorY)
        centerX = scrollX + width / 2f
        centerY = scrollY + height / 2f
        timelineHeight = height / 20f / scaleFactorY            // TODO: Aby uzivatel tuto promennou mohl menit?
        pianoKeyWidth = width / 7f / scaleFactorX               // TODO: Aby uzivatel tuto promennou mohl menit?
        pianoKeyHeight = (height - timelineHeight) / 128f

        barTimeSignature = 4 / 4f
        beatLength = 480
        barLength = barTimeSignature * 4 * beatLength
        tempo = 60

        isPlaying = false
        isRecording = false
        lineOnTime = 0f
        movingTimeLine = false
        elapsedTime = System.currentTimeMillis()
        lastFrameTime = System.currentTimeMillis()
        currentTime = System.currentTimeMillis()

        rectFArrayListInicialization()

        debugAddNotes()

        drawThread = DrawThread()
        drawThread?.start()

        midiPlayer = MidiPlayer()

        /*if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1)
        } else {
            // Permission already granted
            // Write your file-saving code here
        }*/

        val midiFactory = MidiFactory()
        midiFactory.main(context)
        onCreateTestFunction()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Stop drawing on the surface
        drawThread?.stopDrawing()
        drawThread = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        redrawAll()
    }

    private fun redrawAll() {
        var canvas = lockCanvas()
        canvas.save()

        checkBorders()                                      // two times checkBorders, because of clipping out
        widthDifference = width - (width / scaleFactorX)
        heightDifference = height - (height / scaleFactorY)
        checkBorders()

        centerX = scrollX + width / 2f
        centerY = scrollY + height / 2f
        timelineHeight = height / 12f / scaleFactorY
        pianoKeyWidth = width / 7f / scaleFactorX       // TODO: sirka klaves pres nastaveni
        pianoKeyHeight = (height - timelineHeight) / 128f



        canvas.drawColor(Color.GRAY)        // TODO: set background color

        // Zde se provadi transformace sceny
        canvas.translate(-scrollX, -scrollY)
        canvas.scale(scaleFactorX, scaleFactorY, centerX, centerY)


        // rendering
        drawGrid(canvas)
        rescaleRectsOfNotes(notes)
        drawNotes(canvas)
        drawTimelineAndPiano(canvas)
        drawButtons(canvas)

        //drawDebugLines(canvas)

        // playing
        if (isPlaying) {
            currentTime = System.currentTimeMillis()
            elapsedTime = currentTime - lastFrameTime
            lineOnTime += ((tempo / 60f) * beatLength) * elapsedTime / 1000f
            lastFrameTime = currentTime
            playNotes(canvas)
        }

        var midiCreator = MidiCreator()
        var track = Track()
        track.setNotes(notes)
        midiCreator.addTrack(track)
        var midiData = midiCreator.createMidiData(context,4,4, tempo)

        canvas.restore()
        unlockCanvas(canvas)
    }

    public fun stopPlaying() {
        isPlaying = false
    }

    @SuppressLint("RestrictedApi", "MissingPermission")
    private fun onCreateTestFunction() {
        val audioSource = MediaRecorder.AudioSource.MIC
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)

        val audioData = ShortArray(bufferSize)

        audioRecord.startRecording()

        val isRecording = true
        var numSamplesRead = audioRecord.read(audioData, 0, bufferSize)
        var numSamplesReadArray = ArrayList<Int>()
        var counter = 0
        while (isRecording) {
            val buffer = ShortArray(bufferSize)
            val samplesRead = audioRecord.read(buffer, 0, buffer.size)
            numSamplesReadArray.add(numSamplesRead)
            if (counter == 2) {
                break
            }

            var pitch = getFftPitch(buffer, sampleRate)
            println(pitch)

            counter ++
        }

        println(numSamplesReadArray)

        audioRecord.stop()
        audioRecord.release()
    }

    fun getFftPitch(audioData: ShortArray, sampleRate: Int): Double {
        val numSamples = audioData.size
        val real = DoubleArray(numSamples)
        val imag = DoubleArray(numSamples)

        // convert audio samples to array of doubles between -1 and 1
        for (i in 0 until numSamples) {
            real[i] = audioData[i] / 32768.0 // 32768.0 is the maximum value of a signed 16-bit integer
        }

        // apply window function to reduce spectral leakage
        val window = DoubleArray(numSamples)
        for (i in 0 until numSamples) {
            window[i] = 0.54 - 0.46 * cos(2 * PI * i / (numSamples - 1))
            real[i] *= window[i]
        }

        // apply FFT
        val magnitude = DoubleArray(numSamples / 2)
        val phase = DoubleArray(numSamples / 2)
        val n = numSamples / 2
        for (i in 0 until n) {
            var sumReal = 0.0
            var sumImag = 0.0
            for (j in 0 until numSamples) {
                val angle = 2 * PI * i * j / numSamples
                sumReal +=  real[j] * cos(angle) + imag[j] * sin(angle)
                sumImag += -real[j] * sin(angle) + imag[j] * cos(angle)
            }
            magnitude[i] = sqrt(sumReal * sumReal + sumImag * sumImag)
            phase[i] = atan2(sumImag, sumReal)
        }

        // find index of maximum magnitude peak
        var maxIndex = 0
        for (i in 1 until n) {
            if (magnitude[i] > magnitude[maxIndex]) {
                maxIndex = i
            }
        }

        // calculate pitch in Hz
        return sampleRate * maxIndex / numSamples.toDouble()
    }

    fun processPitch(pitchInHz: Float) {
        if (pitchInHz >= 110 && pitchInHz < 123.47) {
            //A
        } else if (pitchInHz >= 123.47 && pitchInHz < 130.81) {
            //B
        } else if (pitchInHz >= 130.81 && pitchInHz < 146.83) {
            //C
        } else if (pitchInHz >= 146.83 && pitchInHz < 164.81) {
            //D
        } else if (pitchInHz >= 164.81 && pitchInHz <= 174.61) {
            //E
        } else if (pitchInHz >= 174.61 && pitchInHz < 185) {
            //F
        } else if (pitchInHz >= 185 && pitchInHz < 196) {
            //G
        }
    }

    private fun lockCanvas(): Canvas {
        return holder.lockCanvas()
    }

    private fun unlockCanvas(canvas: Canvas) {
        holder.unlockCanvasAndPost(canvas)
    }

    private fun checkBorders() {
        if (scaleFactorX < 0.01f) {
            scaleFactorX = 0.01f
        }

        if (scaleFactorY < 1f) {
            scaleFactorY = 1f
        } else if (scaleFactorY > 10f) {
            scaleFactorY = 10f
        }

        if (scrollX + widthDifference / 2f < 0f) {
            scrollX = -widthDifference / 2f
        }

        if (scrollY + heightDifference / 2f < 0f) {
            scrollY = -heightDifference / 2f
        }

        if (scrollY - heightDifference / 2f > 0f) {
            scrollY = heightDifference / 2f
        }
    }

    private fun drawPlayline(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.strokeWidth = 10f / scaleFactorX
        canvas.drawLine(lineOnTime + pianoKeyWidth, 0f, lineOnTime + pianoKeyWidth, height.toFloat(), paint)
        paint.strokeWidth = 0f
    }

    private fun resetTime() {
        lastFrameTime = System.currentTimeMillis()
    }

    private fun drawTimelineAndPiano(canvas: Canvas)  {
        // draw timeline
        paint.color = Color.parseColor("#333333")   // TODO: barvy
        var left = scrollX + widthDifference / 2f
        var right = scrollX + width - (widthDifference / 2f)
        var top = scrollY + heightDifference / 2f
        var bottom = top + timelineHeight
        canvas.drawRect(left, top, right, bottom, paint)

        // draw time checkpoints (bars, ticks, etc.)
        // first visible bar
        var firstBar = left - (left % barLength) + pianoKeyWidth
        var sixteenthLengths = 0
        var actualTime = firstBar
        var topOfTheLine = top
        var upperColor = Color.parseColor("#ffffff")        // TODO: vsechny barvy
        var bottomColor = Color.parseColor("#222222")

        paint.textScaleX = scaleFactorY / scaleFactorX
        var barNumberCorrection = 1 - (pianoKeyWidth / barLength).toInt()
        do {
            var renderLines = true
            // vykreslit vsechny cary
            when (sixteenthLengths % 16) {
                0 -> {
                    topOfTheLine = top
                    upperColor = Color.parseColor("#ffffff")
                    bottomColor = Color.parseColor("#222222")
                    paint.textSize = timelineHeight / 4f
                    paint.color = upperColor
                    canvas.drawText(((actualTime / barLength).toInt() + barNumberCorrection).toString(), actualTime + 5, top + timelineHeight / 4f, paint)
                }

                1, 3, 5, 7, 9, 11, 13, 15 -> {
                    if (scaleFactorX > 0.32f) {
                        topOfTheLine = top + (timelineHeight / 16f * 12f )
                        upperColor = Color.parseColor("#bbbbbb")
                        bottomColor = Color.parseColor("#666666")
                    } else {
                        renderLines = false
                    }
                }

                2, 6, 10, 14 -> {
                    if (scaleFactorX > 0.16f) {
                        topOfTheLine = top + (timelineHeight / 16f * 11f )
                        upperColor = Color.parseColor("#cccccc")
                        bottomColor = Color.parseColor("#555555")
                    } else {
                        renderLines = false
                    }
                }

                4, 12 -> {
                    if (scaleFactorX > 0.08f) {
                        topOfTheLine = top + (timelineHeight / 16f * 10f )
                        upperColor = Color.parseColor("#dddddd")
                        bottomColor = Color.parseColor("#444444")
                    } else {
                        renderLines = false
                    }

                }

                8 -> {
                    if (scaleFactorX > 0.04f) {
                        topOfTheLine = top + (timelineHeight / 16f * 8f )
                        upperColor = Color.parseColor("#eeeeee")
                        bottomColor = Color.parseColor("#333333")
                    } else {
                        renderLines = false
                    }
                }
            }

            if (renderLines) {
                paint.color = upperColor
                canvas.drawLine(actualTime, topOfTheLine, actualTime, bottom, paint)
                paint.color = bottomColor
                canvas.drawLine(actualTime, bottom, actualTime, height.toFloat(), paint)
            }

            actualTime += beatLength / 4f
            sixteenthLengths++

        } while (actualTime < scrollX + width - (widthDifference / 2f))


        // draw playLine
        drawPlayline(canvas)

        // draw piano
        drawPiano(canvas)

        // draw clear area
        paint.color = Color.parseColor("#333333")
        right = pianoKeyWidth + left
        canvas.drawRect(left, top, right, bottom, paint)

        // test draw
    }

    private fun drawGrid(canvas: Canvas) {
        val border = pianoKeyHeight / 20f

        val blackPianoKey = Color.parseColor("#444444")     // TODO: colors
        val whitePianoKey = Color.parseColor("#CCCCCC")
        val left = scrollX + widthDifference / 2f
        val right = left + width / scaleFactorX

        for (i in 0 until 128) {
            // Vykresleni vnejsi casti
            val bottom = height - (i * pianoKeyHeight)
            val top = bottom - pianoKeyHeight

            var key = i % 12

            when (key) {
                0, 2, 4, 5, 7, 9, 11 -> paint.color = whitePianoKey
                1, 3, 6, 8, 10 -> paint.color = blackPianoKey
            }

            canvas.drawRect(left, top, right, bottom, paint)
            when (key) {
                0, 5 -> {
                    paint.color = Color.GRAY
                    canvas.drawRect(left, bottom - border, right, bottom, paint)
                }

                4, 11 -> {
                    paint.color = Color.GRAY
                    canvas.drawRect(left, top, right, top + border, paint)
                }
            }
        }
    }

    private fun rectFArrayListInicialization() {
        inicializeButtons()
        inicializePianoKeys()
    }

    private fun inicializeButtons() {
        buttons.add(0, RectF(0f, 0f, 0f, 0f))   // index 0: play button
        buttons.add(1, RectF(0f, 0f, 0f, 0f))   // index 1: record button
        buttons.add(2, RectF(0f, 0f, 0f, 0f))   // index 2: stop button
    }

    private fun inicializePianoKeys() {
        val left = scrollX + widthDifference / 2f
        val right = pianoKeyWidth + left

        for (i in 0 until 128) {
            // Vykresleni vnejsi casti
            val bottom = height - (i * pianoKeyHeight)
            val top = bottom - pianoKeyHeight
            val rectF = RectF(left, top, right, bottom)
            pianoKeys.add(rectF)
        }
    }

    private fun drawButtons(canvas: Canvas) {
        // Setting top and bottom pixels of buttons
        val top = scrollY + heightDifference / 2f
        val buttonBottom = top + (height - height / 30f) / scaleFactorY
        val buttonTop = top + (height - height / 10f) / scaleFactorY
        val buttonHeight = (buttonBottom - buttonTop) * scaleFactorY / scaleFactorX
        val buttonCenterY = buttonTop + (buttonBottom - buttonTop) / 2f
        val playButtonLeft = (width / 2f) - (buttonHeight / 2f) + scrollX
        val playButtonRight = (width / 2f) + (buttonHeight / 2f) + scrollX

        buttons[0] = RectF(playButtonLeft, buttonTop, playButtonRight, buttonBottom)
        buttons[1] = RectF(playButtonLeft - buttonHeight * 1.2f, buttonTop, playButtonRight - buttonHeight * 1.2f, buttonBottom)
        buttons[2] = RectF(playButtonLeft + buttonHeight * 1.2f, buttonTop, playButtonRight + buttonHeight * 1.2f, buttonBottom)

        if (isPlaying || isRecording) {
            paint.color = Color.GRAY                    // TODO: color
            canvas.drawOval(buttons[0], paint)          // play button background
            canvas.drawOval(buttons[1], paint)          // record button background
            paint.color = Color.WHITE                   // TODO: color
            canvas.drawOval(buttons[2], paint)          // stop button background
        } else {
            paint.color = Color.WHITE                   // TODO: color
            canvas.drawOval(buttons[0], paint)          // play button background
            canvas.drawOval(buttons[1], paint)          // record button background
            paint.color = Color.GRAY                    // TODO: color
            canvas.drawOval(buttons[2], paint)          // stop button background
        }

        // draw play button symbol
        val heightCorrection = (buttonBottom - buttonTop) / 5f
        val widthCorrection = (playButtonRight - playButtonLeft) / 4f;
        val triangleVerticies = floatArrayOf(
            playButtonLeft + widthCorrection, buttonTop + heightCorrection,  // top vertex
            playButtonLeft + widthCorrection, buttonBottom - heightCorrection,  // bottom vertex
            playButtonRight - widthCorrection, buttonCenterY   // right vertex
        )

        val colors = intArrayOf(
            Color.BLACK, Color.BLACK, Color.BLACK, -0x1000000, -0x1000000, -0x1000000         // TODO: color
        )

        val vertexCount = triangleVerticies.size
        canvas.drawVertices(
            VertexMode.TRIANGLES, vertexCount, triangleVerticies,
            0,null,0,
            colors.map { it.toInt() }.toIntArray(),
            0, null,0, 0, paint
        )

        // draw record button symbol
        paint.color = Color.RED
        var symbolHeightCorrection = (buttonBottom - buttonTop) / 3.5f
        var symbolWidthCorrection = (playButtonRight - playButtonLeft) / 3.5f
        canvas.drawOval(buttons[1].left + symbolWidthCorrection,
            buttons[1].top + symbolHeightCorrection,
            buttons[1].right - symbolWidthCorrection,
            buttons[1].bottom - symbolHeightCorrection, paint)

        paint.color = Color.BLACK
        symbolHeightCorrection = (buttonBottom - buttonTop) / 3.2f
        symbolWidthCorrection = (playButtonRight - playButtonLeft) / 3.2f
        canvas.drawRect(buttons[2].left + symbolWidthCorrection,
            buttons[2].top + symbolHeightCorrection,
            buttons[2].right - symbolWidthCorrection,
            buttons[2].bottom - symbolHeightCorrection, paint)

        // TODO: edit button
    }

    // TODO: Budou potreba tyto metody pro konvert?
    private fun pitchToHeightConverter(pitch: Int): Float {
        return height / 2f  // FIXME: placeholder
    }

    private fun heightToPitchConverter(height: Float): Int {
        return 60           // FIXME: placeholder
    }

    private fun pitchToNameConverter(pitch: Int): String {
        var noteName = ""

        when (pitch % 12) {
            0 -> noteName += "c"
            1 -> noteName += "cis"
            2 -> noteName += "d"
            3 -> noteName += "dis"
            4 -> noteName += "e"
            5 -> noteName += "f"
            6 -> noteName += "fis"
            7 -> noteName += "g"
            8 -> noteName += "gis"
            9 -> noteName += "a"
            10 -> noteName += "ais"
            11 -> noteName += "b"
        }

        var scaleNumber = (pitch / 12) - 2
        var scaleStringNumber = scaleNumber.toString().replace("-", "m")
        noteName += scaleStringNumber
        return noteName
    }

    private fun drawNotes(canvas: Canvas) {
        notes.forEach {
            drawNote(canvas, it)        // TODO: barva noty
        }
    }

    // TODO: Je potreba barva jako vstupni atribut?
    private fun drawNote(canvas: Canvas, note: Note) {
        val border = pianoKeyHeight / 20f

        // Namalovat okraje
        var noteRectF = note.rectF
        paint.color = Color.DKGRAY              // TODO: barvy
        canvas.drawRect(noteRectF, paint)

        paint.color = Color.BLUE
        // Namalovat vnitrek
        selectedNotes.forEach {
            if (it == note) {
                paint.color = Color.YELLOW
            }
        }

        canvas.drawRect(noteRectF.left + border, noteRectF.top + border, noteRectF.right - border, noteRectF.bottom - border, paint)
    }

    private fun drawPiano(canvas: Canvas) {
        // Draw piano keys
        val border = pianoKeyHeight / 20f

        val blackPianoKey = Color.BLACK
        val whitePianoKey = Color.WHITE

        val left = scrollX + widthDifference / 2f
        val right = pianoKeyWidth + left

        pianoKeys.forEachIndexed { i, it ->
            it.left = left
            it.right = right
            it.bottom = height - (i * pianoKeyHeight)
            it.top = it.bottom - pianoKeyHeight

            var key = i % 12
            when (key) {
                0, 2, 4, 5, 7, 9, 11 -> paint.color = whitePianoKey
                1, 3, 6, 8, 10 -> paint.color = blackPianoKey
            }

            canvas.drawRect(it, paint)
            when (key) {
                0 -> {
                    paint.color = Color.GRAY
                    canvas.drawRect(it.left, it.bottom - border, it.right, it.bottom, paint)

                    val scaleNumber = (i / 12) - 2
                    paint.textSize = pianoKeyHeight * 0.6f
                    paint.color = Color.DKGRAY
                    canvas.drawText("C$scaleNumber", it.left + 2f, it.bottom - pianoKeyHeight * 0.15f, paint)
                }

                5 -> {
                    paint.color = Color.GRAY
                    canvas.drawRect(it.left, it.bottom - border, it.right, it.bottom, paint)
                }

                4, 11 -> {
                    paint.color = Color.GRAY
                    canvas.drawRect(it.left, it.top, it.right, it.top + border, paint)
                }
            }
        }
    }

    private fun playNotes(canvas: Canvas) {
        notes.forEach {
            if (lineOnTime >= it.start) {
                if (playingNotes.contains(it)) {
                    if (lineOnTime > it.start + it.duration) {
                        // stop note
                        playingNotes.remove(it)
                        midiPlayer.stopNote(it.pitch)
                    }
                } else {
                    if (lineOnTime > it.start + it.duration) {

                    } else {
                        // play note
                        playingNotes.add(it)
                        midiPlayer.playNote(it.pitch)
                    }
                }
            }
        }
    }

    fun getNotes(): ArrayList<Note> {
        return this.notes
    }

    fun setNotes(notes: ArrayList<Note>) {
        this.notes = notes
        //redrawAll()
    }

    fun rescaleRectsOfNotes(notes: ArrayList<Note>) {
        notes.forEach{
            it.rectF = getRectFromNoteInfo(it.pitch, it.start, it.duration)
        }
    }

    fun getRectFromNoteInfo(pitch: Byte, start: Int, duration: Int): RectF {
        var bottom = height - (pitch * pianoKeyHeight)
        var top = bottom - pianoKeyHeight
        var left = start + pianoKeyWidth       // Posunuji o sirku klaves
        var right = left + duration
        return RectF(left, top, right, bottom)
    }

    private fun onSingleTapUpEvent(eventX: Float, eventY: Float) {
        // play button
        // FIXME: not updated
        if(buttons[0].contains(eventX, eventY)) {
            if (!isPlaying && !isRecording) {
                isPlaying = true
                drawThread = DrawThread()
                drawThread?.start()
                resetTime()
                midiPlayer.onMidiStart()
            }
        } else if (buttons[1].contains(eventX, eventY)) {
            if (!isRecording && !isPlaying) {
                isRecording = true
                // TODO: start recording
            }
        } else if (buttons[2].contains(eventX, eventY)) {
            if (isPlaying) {
                isPlaying = false
                drawThread?.stopDrawing()
                drawThread = null
                playingNotes.clear()
                midiPlayer.stopAllNotes()
            }

            if (isRecording) {
                isRecording = false
                // TODO: stop recording
            }
        }
    }

    private fun onDownEvent(eventX: Float, eventY: Float) {
        // timeline
        var top = scrollY + heightDifference / 2f
        var bottom = top + timelineHeight
        movingTimeLine = false
        if (convertEventY(eventY) > top && convertEventY(eventY) <= bottom) {
            // taplo se na timeline -> presunout line
            isPlaying = false
            movingTimeLine = true
            if (convertEventX(eventX) - pianoKeyWidth > 0) {
                lineOnTime = convertEventX(eventX) - pianoKeyWidth
            } else {
                lineOnTime = 0f
            }
        }

        // piano keys
        var left = pianoKeys.first().left
        var right = pianoKeys.first().right
        top = pianoKeys.last().top
        bottom = pianoKeys.first().bottom

        if (convertEventX(eventX) >= left && convertEventX(eventX) <= right) {
            pianoKeys.forEachIndexed {i, it ->
                if (it.contains(convertEventX(eventX), convertEventY(eventY))) {
                    GlobalScope.launch {
                        midiPlayer.playNote(i.toByte())
                        delay(500)
                        midiPlayer.stopNote(i.toByte())
                    }
                }
            }
        }
    }

    private fun onScrollingEvent(eventX1: Float, eventY1: Float, eventX2: Float, eventY2: Float) {
        // timeline
        if (movingTimeLine) {
            if (convertEventX(eventX2) - pianoKeyWidth > 0) {
                lineOnTime = convertEventX(eventX2) - pianoKeyWidth
            } else {
                lineOnTime = 0f
            }
        }
    }

    private fun convertEventX(eventX: Float): Float {
        return scrollX + ((width - width / scaleFactorX) / 2f) + (eventX / scaleFactorX)
    }

    private fun convertEventY(eventY: Float): Float {
        return scrollY + ((height - height / scaleFactorY) / 2f) + (eventY / scaleFactorY)
    }

    override fun onDown(event: MotionEvent): Boolean {
        /*println("------- ON DOWN -------")
        println("X: " + event.x + " |Y: " + event.y)*/

        onDownEvent(event.x, event.y)
        return true
    }

    override fun onShowPress(event: MotionEvent) {
        /*println("------- ON SHOW PRESS -------")
        println("X: " + event.x + " |Y: " + event.y)*/
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        /*println("------- ON SINGLE TAP -------")
        println("X: " + event.x + " |Y: " + event.y)*/
        val actualTapX = convertEventX(event.x)
        val actualTapY = convertEventY(event.y)

        onSingleTapUpEvent(actualTapX, actualTapY)
        return true
    }

    override fun onScroll(event1: MotionEvent, event2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        /*println("------- ON SCROLL -------")
        println("DOWN - X: " + event1.x + " |Y: " + event1.y)
        println("DOWN - X: " + event2.x + " |Y: " + event2.y)
        println("DISTANCE - X: " + distanceX + " |Y: " + distanceY)*/
        if (!scaling && !movingTimeLine) {
            scrollX += distanceX / scaleFactorX
            scrollY += distanceY / scaleFactorY
        }

        onScrollingEvent(event1.x, event1.y, event2.x, event2.y)
        return true
    }

    override fun onLongPress(event: MotionEvent) {
        /*println("------- ON LONG PRESS -------")
        println("X: " + event.x + " |Y: " + event.y)*/
    }

    override fun onFling(eventDown: MotionEvent, eventUp: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        /*println("------- ON FLING -------")
        println("DOWN - X: " + eventDown.x + " |Y: " + eventDown.y)
        println("UP - X: " + eventUp.x + " |Y: " + eventUp.y)
        println("VELOCITY - X: " + velocityX + " |Y: " + velocityY)*/
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        /*println("OnScale")
        println("----------"+detector.scaleFactor)
        println("-" + detector.focusX + "+" + detector.focusY)*/

        if (scalingX && !movingTimeLine) {
            scaleFactorX *= detector.scaleFactor
        }

        if (scalingY && !movingTimeLine) {
            scaleFactorY *= detector.scaleFactor
        }

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        scaling = true
        startedSpanX = detector.currentSpanX
        startedSpanY = detector.currentSpanY

        if (detector.currentSpanX * 0.8f > detector.currentSpanY) {
            scalingX = true
        } else if (detector.currentSpanY * 0.8f > detector.currentSpanX) {
            scalingY = true
        } else {
            scalingY = true
            scalingX = true
        }

        //println("OnScaleBegin")
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        scaling = false
        scalingX = false
        scalingY = false
        //println("OnScaleEnd")
    }

    private fun drawDebugLines(canvas: Canvas) {
        for (i in 0 until 20) {
            if (i % 2 == 0) {
                paint.color = Color.RED
            } else {
                paint.color = Color.GREEN
            }

            canvas.drawRect(i * 100f, 0f, (i * 100f) + 100f, 20f, paint)
        }

        for (i in 0 until 20) {
            if (i % 2 == 0) {
                paint.color = Color.RED
            } else {
                paint.color = Color.GREEN
            }

            canvas.drawRect(0f, i * 100f, 20f, (i * 100f) + 100f, paint)
        }

        canvas.drawRect(400f, 400f, 800f, 800f, paint)

        paint.color = Color.BLUE
        canvas.drawRect(centerX - 50f, centerY - 50f, centerX + 50f, centerY + 50f, paint)

        // Random vertex
        paint.color = Color.RED
        val vertices = floatArrayOf(
            100f, 100f,  // first vertex
            200f, 100f,  // second vertex
            150f, 200f   // third vertex
        )

        val colors = intArrayOf(
            Color.BLUE, Color.BLUE, Color.BLUE, -0x1000000, -0x1000000, -0x1000000
        )

        val vertexCount = vertices.size

        canvas.drawVertices(
            VertexMode.TRIANGLES, vertexCount, vertices,
            0,null,0,
            colors.map { it.toInt() }.toIntArray(),
            0, null,0, 0, paint
        )
    }

    private fun debugAddNotes() {
        var rectF = getRectFromNoteInfo(60, 0, 480)
        var note = Note(60, 0,480, rectF)
        notes.add(note)

        selectedNotes.add(note)

        rectF = getRectFromNoteInfo(64, 240,480)
        note = Note(64, 240,960, rectF)
        notes.add(note)

        rectF = getRectFromNoteInfo(64, 1440,960)
        note = Note(60, 1440,480, rectF)
        notes.add(note)

        rectF = getRectFromNoteInfo(64, 1440,960)
        note = Note(64, 1440,960, rectF)
        notes.add(note)
    }
}