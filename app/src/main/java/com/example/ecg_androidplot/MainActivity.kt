package com.example.ecg_androidplot

import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.androidplot.ui.HorizontalPositioning
import com.androidplot.ui.LayoutManager
import com.androidplot.ui.VerticalPositioning
import com.androidplot.util.Redrawer
import com.androidplot.xy.*
import java.lang.ref.WeakReference
import kotlin.math.cos
import kotlin.math.sin


/**
 * A simple XYPlot
 */
class MainActivity : Activity() {

    private var plot: XYPlot? = null
    private var redrawer: Redrawer? = Redrawer(plot,30f,false)
    private var data_size = 2000

    private var mediaPlayer: MediaPlayer? = null

    public override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaPlayer = MediaPlayer.create(this,R.raw.ecg_beep)
        mediaPlayer?.isLooping=true
        mediaPlayer?.start()

        plot = findViewById(R.id.ecg_plot)
        val content = object {}.javaClass.getResource("/res/raw/pattern3.txt")!!.readText()
        Toast.makeText(applicationContext, "File loaded", Toast.LENGTH_SHORT).show()
        val content2 = content.split("\t").map { it.toFloat() } // ERROR
        //val series1 = SimpleXYSeries( content2, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY,"Series1")
        val  series1Format = LineAndPointFormatter(Color.RED, null, null, null)
        series1Format.interpolationParams =
            CatmullRomInterpolator.Params(2, CatmullRomInterpolator.Type.Centripetal)
        //plot!!.addSeries(series1, series1Format)

        val ecgSeries = ECGModel(data_size, 200, content2)

        series1Format.isLegendIconEnabled = false

        plot!!.addSeries(ecgSeries, series1Format)
        plot!!.setRangeBoundaries(-0.6, 1.8, BoundaryMode.FIXED) // Y axis boundaries
        plot!!.setDomainBoundaries(0, data_size, BoundaryMode.FIXED) // X axis boundaries
        plot!!.graph.position(0f,HorizontalPositioning.RELATIVE_TO_LEFT,0f,VerticalPositioning.RELATIVE_TO_TOP)

        plot!!.rangeTitle.isVisible = false
        plot!!.legend.isVisible = false
        plot!!.linesPerRangeLabel = 1  // reduce the number of range labels

        // start generating ecg data in the background:
        ecgSeries.start(WeakReference(plot!!.getRenderer(AdvancedLineAndPointRenderer::class.java)))
        redrawer = Redrawer(plot, 60f, true) //Android plot object to force a plot redrawn every x seconds
    }

    class ECGModel internal constructor(size: Int, updateFreqHz: Int,values: List<Float> ) : XYSeries {

        private val data: Array<Number> = Array(size) { 0 }
        private val delayMs: Long
        private val thread: Thread
        private var keepRunning = true
        private var latestIndex = 0
        private var rendererRef: WeakReference<AdvancedLineAndPointRenderer?>? = null

        init {
            var y :Int = 0

            for (i in data.indices) { data[i] = 0 }
            // translate hz into delay (ms):
            delayMs = 1000 / updateFreqHz.toLong()

            thread = Thread(Runnable {
                try {

                    while (keepRunning) {
                        if (latestIndex >= data.size) {
                            latestIndex = 0
                        }
                        data[latestIndex] = values[y]
                        if(y == values.size-1){
                            y=0
                        }
                        else {y++}

                        if (latestIndex < data.size - 1) { // null out the point immediately following i, to disable
                            // connecting i and i+1 with a line:
                            data[latestIndex + 1] = 0
                        }
                        if (rendererRef!!.get() != null) { //ERROR ?
                            rendererRef!!.get()!!.setLatestIndex(latestIndex)
                            Thread.sleep(delayMs)
                        } else {
                            keepRunning = true
                        }
                        latestIndex++
                        Thread.sleep(delayMs)

                    }
                } catch (e: InterruptedException) {
                    Log.i("DEBUG","catch")
                    keepRunning = false
                }
            })
        }

        fun start(rendererRef: WeakReference<AdvancedLineAndPointRenderer?>?) {
            this.rendererRef = rendererRef
            keepRunning = true
            thread.start()
        }

        override fun size(): Int {
            return data.size
        }

        override fun getX(index: Int): Number {
            return index
        }

        override fun getY(index: Int): Number {
            return data[index]
        }

        override fun getTitle(): String {
            return "Signal"
        }
    }

    public override fun onStop() {
        super.onStop()
        redrawer!!.finish()
    }
}