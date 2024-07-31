package cz.uhk.diplomovaprace

import ProjectAdapter
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cz.uhk.diplomovaprace.Project.ProjectManager
import cz.uhk.diplomovaprace.Project.ProjectViewModel

class SavedFilesFragment : Fragment() {

    private val viewModel: ProjectViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_saved_files, container, false)
        val linearLayoutInner: LinearLayout = view.findViewById(R.id.linearLayoutInner)
        linearLayoutInner.setOnClickListener {
            findNavController().navigate(R.id.action_savedFilesFragment_to_newProjectSettingsFragment)
        }

        val projectManager = ProjectManager()

        // Získání seznamu projektů
        val projects = projectManager.loadProjectsFromFile(context)

        val recyclerView: RecyclerView = view.findViewById(R.id.projektyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val adapter = ProjectAdapter(projects, viewModel, findNavController())
        recyclerView.adapter = adapter

        adapter.setOnMenuClickListener(object : ProjectAdapter.OnMenuClickListener {
            override fun onMenuClick(view: View, position: Int) {
                val popup = PopupMenu(requireContext(), view)
                popup.inflate(R.menu.project_item_menu)

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_rename -> {
                            // Handle rename action for the project at 'position'
                            true
                        }

                        R.id.action_delete -> {
                            // Handle delete action for the project at 'position'
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }
        })

        return view
    }
}