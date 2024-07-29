package cz.uhk.diplomovaprace

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import cz.uhk.diplomovaprace.PianoRoll.PianoRollView
import cz.uhk.diplomovaprace.Project.Project
import cz.uhk.diplomovaprace.Project.ProjectViewModel


/**
 * A simple [Fragment] subclass.
 * Use the [PianoRollFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PianoRollFragment : Fragment() {

    private lateinit var pianoRollView: PianoRollView
    private lateinit var playButton: ImageView
    private lateinit var recordButton: ImageView
    private lateinit var stopButton: ImageView

    private val viewModel: ProjectViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_piano_roll, container, false)
        pianoRollView = view.findViewById(R.id.piano_roll_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val its = viewModel.selectedProject.value
        // set the data for the piano roll view
        viewModel.selectedProject.observe(viewLifecycleOwner) { project ->
            pianoRollView.loadProject(project)
        }

        // Get the buttons from the view
        playButton = view.findViewById(R.id.imageView4)
        recordButton = view.findViewById(R.id.imageView5)
        stopButton = view.findViewById(R.id.imageView6)

        playButton.alpha = 1f
        recordButton.alpha = 1f
        stopButton.alpha = 0.3f

        // Set the onClickListeners for the buttons
        playButton.setOnClickListener {
            // Call the play function from PianoRollView
            pianoRollView.pushPlayButton()
            updateButtonStates()
        }

        recordButton.setOnClickListener {
            // Call the record function from PianoRollView
            pianoRollView.pushRecordButton()
            updateButtonStates()
        }

        stopButton.setOnClickListener {
            // Call the stop function from PianoRollView
            pianoRollView.pushStopButton()
            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        if (pianoRollView.isPlaying || pianoRollView.isRecording) {
            playButton.alpha = 0.3f
            recordButton.alpha = 0.3f
            stopButton.alpha = 1f
        } else {
            playButton.alpha = 1f
            recordButton.alpha = 1f
            stopButton.alpha = 0.3f
        }
    }
}