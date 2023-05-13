package com.yaroslavgamayunov.toodoo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.yaroslavgamayunov.toodoo.R
import com.yaroslavgamayunov.toodoo.data.model.TaskPriority
import com.yaroslavgamayunov.toodoo.databinding.FragmentTaskEditBinding
import com.yaroslavgamayunov.toodoo.domain.entities.Task
import com.yaroslavgamayunov.toodoo.domain.entities.TaskScheduleMode
import com.yaroslavgamayunov.toodoo.domain.entities.scheduleMode
import com.yaroslavgamayunov.toodoo.ui.base.BaseFragment
import com.yaroslavgamayunov.toodoo.ui.viewmodel.TaskEditViewModel
import com.yaroslavgamayunov.toodoo.ui.viewmodel.TooDooViewModelFactory
import com.yaroslavgamayunov.toodoo.util.appComponent
import com.yaroslavgamayunov.toodoo.util.getColorFromAttrs
import com.yaroslavgamayunov.toodoo.util.getColoredText
import com.yaroslavgamayunov.toodoo.util.localEpochMilliToZonedDateTime
import com.yaroslavgamayunov.toodoo.util.simpleFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject


class TaskEditFragment : BaseFragment() {

    @Inject
    lateinit var viewModelFactory: TooDooViewModelFactory
    private val taskEditViewModel: TaskEditViewModel by viewModels { viewModelFactory }

    private var binding: FragmentTaskEditBinding? = null
    private val args: TaskEditFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().application.appComponent!!.inject(this)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_task_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentTaskEditBinding.bind(view)
        binding!!.apply {
            priorityPicker.setOnClickListener {
                showPriorityMenu()
            }

            dateTimePicker.setOnClickListener {
                showDatePicker()
            }

            taskTimeSwitch.setOnClickListener {
                if (taskTimeSwitch.isChecked) {
                    showDatePicker()
                } else {
                    taskEditViewModel.disableDeadline()
                }
            }

            taskDescriptionEditText.addTextChangedListener {
                taskEditViewModel.updateDescription(it.toString())
            }

            taskDeleteButton.setOnClickListener {
                showTaskDeletionDialog()
            }
        }
        lifecycleScope.launch {
            taskEditViewModel.task.flowWithLifecycle(lifecycle).collect {
                updateLayout(it)
            }
        }
        args.taskId?.let {
            taskEditViewModel.loadTaskForEditing(it)
        }
        setupToolbar()
    }

    private fun updateLayout(task: Task) {
        binding?.apply {
            taskDescriptionEditText.apply {
                setText(task.description)
                setSelection(task.description.length)
            }

            val scheduleMode = task.scheduleMode

            taskDeadlineTextView.text =
                if (scheduleMode == TaskScheduleMode.Unspecified) ""
                else task.deadline.simpleFormat(
                    showTime = scheduleMode == TaskScheduleMode.ByTime
                )

            val priorities = requireActivity().resources.getStringArray(R.array.task_priorities)
            priorityTextView.text = priorities[task.priority.level]

            taskTimeSwitch.isChecked = (scheduleMode == TaskScheduleMode.Unspecified).not()
        }
    }


    private fun showPriorityMenu() {
        showMenu(
            binding!!.priorityTextView,
            R.menu.menu_task_priority,
            onMenuInflated = { popupMenu ->
                popupMenu.menu.findItem(R.id.high).let {
                    it.title = it.title?.getColoredText(
                        requireActivity().getColorFromAttrs(R.attr.tooDooRed)
                    )
                }
            },
            onItemClick = {
                when (it.itemId) {
                    R.id.none -> {
                        taskEditViewModel.updatePriority(TaskPriority.None)
                        true
                    }

                    R.id.low -> {
                        taskEditViewModel.updatePriority(TaskPriority.Low)
                        true
                    }

                    R.id.high -> {
                        taskEditViewModel.updatePriority(TaskPriority.High)
                        true
                    }

                    else -> false
                }
            }
        )
    }

    private fun showDatePicker() {
        val constraints =
            CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now()).build()

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(requireActivity().getString(R.string.datepicker_title))
                .setCalendarConstraints(constraints)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()
        datePicker.addOnPositiveButtonClickListener { time ->
            val zonedDateTime = time.localEpochMilliToZonedDateTime()
            taskEditViewModel.updateDeadline(zonedDateTime)

            showTimePicker(zonedDateTime)
        }

        datePicker.addOnDismissListener {
            if (taskEditViewModel.task.value.scheduleMode == TaskScheduleMode.Unspecified) {
                binding!!.taskTimeSwitch.isChecked = false
            }
        }

        datePicker.show(childFragmentManager, null)
    }

    private fun showTimePicker(date: ZonedDateTime) {
        val picker =
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText(requireActivity().getString(R.string.timepicker_title))
                .build()

        picker.addOnPositiveButtonClickListener {
            val selectedDateAndTime = date
                .plus(picker.hour.toLong(), ChronoUnit.HOURS)
                .plus(picker.minute.toLong(), ChronoUnit.MINUTES)

            taskEditViewModel.updateDeadline(selectedDateAndTime)
        }

        picker.show(childFragmentManager, null)
    }

    private fun setupToolbar() {
        binding?.apply {
            taskEditFragmentToolbar.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.actionSave -> runBlocking {
                        taskEditViewModel.saveChanges()
                        findNavController().navigateUp()
                        true
                    }

                    else -> false
                }
            }
            taskEditFragmentToolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun deleteCurrentTask() {
        if (args.taskId != null) {
            taskEditViewModel.deleteTask()
        }
        findNavController().navigateUp()
    }

    private fun showTaskDeletionDialog() {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder
            .setMessage(R.string.task_deletion_dialog_title)
            .setPositiveButton(R.string.delete) { _, _ -> deleteCurrentTask() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
        builder.create().show()
    }
}