package com.example.week2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.week2.data.meal.WordsApplication
import com.example.week2.ui.adapter.MealListAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MealActivity : AppCompatActivity(), MealListAdapter.OnItemClickListener {

    private val mealViewModel: MealViewModel by viewModels {
        MealViewModelFactory((application as WordsApplication).mealRepository)
    }
    private val adapter = MealListAdapter(this)
    private lateinit var newMealActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var editMealActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupActivityLaunchers()
        observeViewModel()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerview)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFab() {
        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this@MealActivity, NewMealActivity::class.java)
            newMealActivityLauncher.launch(intent)
        }
    }

    private fun setupActivityLaunchers() {
        newMealActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> handleNewMealActivityResult(result.resultCode, result.data) }

        editMealActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> handleEditMealActivityResult(result.resultCode, result.data) }
    }

    private fun handleNewMealActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val meal = createMealFromIntent(data)
            mealViewModel.insert(meal)
        } else {
            showToast(R.string.meal_empty_not_saved)
        }
    }

    private fun handleEditMealActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val meal = createMealFromIntent(data, data.getIntExtra(EditMealActivity.EXTRA_MEAL_ID, -1))
            mealViewModel.update(meal)
        } else {
            showToast(R.string.meal_empty_not_saved)
        }
    }

    private fun createMealFromIntent(data: Intent, id: Int = 0): Meal {
        val mealTime = data.getStringExtra(NewMealActivity.EXTRA_MEAL_TIME) ?: ""
        val mealName = data.getStringExtra(NewMealActivity.EXTRA_MEAL_NAME) ?: ""
        val price = data.getIntExtra(NewMealActivity.EXTRA_PRICE, 0)
        val date = data.getStringExtra(NewMealActivity.EXTRA_DATE) ?: ""
        val memo = data.getStringExtra(NewMealActivity.EXTRA_MEMO)

        return Meal(id, mealTime, mealName, price, date, memo)
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(applicationContext, messageResId, Toast.LENGTH_LONG).show()
    }

    private fun observeViewModel() {
        mealViewModel.todayMeals.observe(this) { meals ->
            meals.let { adapter.submitList(it) }
        }
    }

    override fun onItemClick(position: Int) {
        val selectedMeal = adapter.currentList[position]
        val intent = Intent(this@MealActivity, EditMealActivity::class.java).apply {
            putExtra(EditMealActivity.EXTRA_MEAL_ID, selectedMeal.id)
            putExtra(EditMealActivity.EXTRA_MEAL_TIME, selectedMeal.mealTime)
            putExtra(EditMealActivity.EXTRA_MEAL_NAME, selectedMeal.mealName)
            putExtra(EditMealActivity.EXTRA_PRICE, selectedMeal.price)
            putExtra(EditMealActivity.EXTRA_DATE, selectedMeal.date)
            putExtra(EditMealActivity.EXTRA_MEMO, selectedMeal.memo)
        }
        editMealActivityLauncher.launch(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}