package org.alienz.heatermeter.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColor
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.alienz.heatermeter.R
import org.alienz.heatermeter.data.NamesViewModel
import org.alienz.heatermeter.data.Sample
import org.alienz.heatermeter.data.SamplesViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


/**
 * A simple [Fragment] subclass.
 * Use the [ChartFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ChartFragment : Fragment() {
    private lateinit var namesViewModel: NamesViewModel
    private lateinit var samplesViewModel: SamplesViewModel

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        namesViewModel = ViewModelProviders.of(this).get(NamesViewModel::class.java)
        samplesViewModel = ViewModelProviders.of(this).get(SamplesViewModel::class.java)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.chart_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lineChart: LineChart = view.findViewById(R.id.chart)

        lineChart.setTouchEnabled(true)

        lineChart.xAxis.apply {
            valueFormatter = object : ValueFormatter() {
                val formatter = DateTimeFormatter.ofPattern("hh:mm")

                override fun getFormattedValue(value: Float): String {
                    // undo the floating point hack
                    val instant = value.timeHack().atZone(ZoneId.systemDefault())
                    return formatter.format(instant)
                }
            }

            position = XAxis.XAxisPosition.BOTTOM
            mEntries
        }

        lineChart.axisLeft.apply {
            axisMaximum = 100.0f
            axisMinimum = 0.0f
            setDrawGridLines(false)
        }

        lineChart.axisRight.apply {
            axisMaximum = 350.0f
            axisMinimum = 0.0f
        }

        samplesViewModel
            .recentSamples()
            .observe(
                viewLifecycleOwner,
                Observer { samples -> updateChartData(lineChart, samples) })
    }

    private fun updateChartData(lineChart: LineChart, samples: List<Sample>) {
        if (samples.isEmpty()) {
            lineChart.clear()
            return
        }

        Log.i(this::class.java.simpleName, "Updating chart with new samples")
        val entries = mutableMapOf<Int, MutableList<Entry>>()

        // create a LineData container on the first observation
        if (lineChart.data == null) {
            lineChart.data = LineData()
        }

        val lineData = lineChart.data

        samples.forEach {
            // work around fp32 precision issues by making the X axis relative to the class time
            val x = it.time.timeHack()
            val entry = { y: Float -> Entry(x, y) }

            entries.getOrPut(0, { mutableListOf() }).add(entry(it.fan.rpm.toFloat()))
            entries.getOrPut(1, { mutableListOf() }).add(entry(if (it.lidOpen) 1.0f else 0.0f))
            entries.getOrPut(2, { mutableListOf() }).add(entry(it.setPoint.degrees.toFloat()))

            it.probes.forEach { probe ->
                if (probe.temperature.degrees > 0.0) {
                    entries.getOrPut(3 + probe.index, { mutableListOf() })
                        .add(entry(probe.temperature.degrees.toFloat()))
                }
            }
        }

        // remove data from datasets without samples
        lineData.dataSets.indices
            .subtract(entries.keys)
            .forEach{ lineData.dataSets[it].clear() }

        // create datasets for new series
        (lineData.dataSetCount .. (entries.keys.max() ?: 0)).forEach { _ ->
            lineData.addDataSet(LineDataSet(mutableListOf(), "foo"))
        }

        entries.forEach { (k, v) ->
            val lineDataSet = lineData.dataSets[k] as LineDataSet
            if (k == 0 || k == 1) {
                lineDataSet.axisDependency = YAxis.AxisDependency.LEFT
            } else {
                lineDataSet.axisDependency = YAxis.AxisDependency.RIGHT
            }

            val alpha = 0xff shl 24

            lineDataSet.color = when (k) {
                0 -> 0x50a4d1
                2 -> 0xb9333b
                3 -> 0xee7733
                4 -> 0x66cc33
                5 -> 0xf229977
                else -> -0x7feeeeee
            }.let { alpha or it }.toColor().toArgb()

            lineDataSet.lineWidth = 2.0f
            lineDataSet.setDrawCircles(false)
            lineDataSet.setDrawValues(false)
            lineDataSet.values = v
        }

        lineData.notifyDataChanged()
        lineChart.notifyDataSetChanged()

        // round the intervals down to the hour
        val xAxisEntries = lineChart.xAxis.mEntries
        for (i in xAxisEntries.indices) {
            val truncated = xAxisEntries[i].timeHack().truncatedTo(ChronoUnit.HOURS)
            xAxisEntries[i] = truncated.timeHack()
        }

        lineChart.postInvalidate()
    }

    companion object {
        // TODO: Rename parameter arguments, choose names that match
        // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"
        private val CLASS_LOAD_TIME = Instant.now()

        fun Float.timeHack(): Instant {
            return CLASS_LOAD_TIME.plusSeconds(this.toLong())
        }

        fun Instant.timeHack(): Float {
            // work around fp32 precision issues by making the X axis relative to the class load time
            return Duration.between(CLASS_LOAD_TIME, this).seconds.toFloat()
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ChartFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ChartFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}