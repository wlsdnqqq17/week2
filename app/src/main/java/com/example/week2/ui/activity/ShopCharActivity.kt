package com.example.week2

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.week2.data.AppRoomDatabase
import com.example.week2.data.item.Item
import com.example.week2.data.item.ItemRepository
import com.example.week2.data.item.ItemViewModel
import com.example.week2.data.item.ItemViewModelFactory
import com.example.week2.data.meal.WordsApplication
import com.example.week2.ui.adapter.ItemListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ShopCharActivity : AppCompatActivity(), ItemListAdapter.OnItemClickListener {
    private lateinit var apiService: ApiService
    private lateinit var repository: ItemRepository
    private val itemViewModel: ItemViewModel by viewModels {
        ItemViewModelFactory((application as WordsApplication).itemRepository)
    }
    private val adapter = ItemListAdapter(this)
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var potatoCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        // Initialize SharedPreferences and TextView
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        potatoCount = findViewById(R.id.potato_count)

        // Set saved potato count to TextView
        val savedInt = sharedPreferences.getInt("Potato", 0)
        potatoCount.text = "$savedInt"

        setupToolbar()
        setupRecyclerView()
        apiService = RetrofitClient.getInstance().create(ApiService::class.java)

        val db = AppRoomDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
        repository = ItemRepository(db.itemDao())
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
        val spanCount = 2 // 한 줄에 표시할 항목 수
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun observeViewModel() {
        itemViewModel.getItemsByCategory("clothes").observe(this) { items ->
            items.let {
                adapter.submitList(it)
                Log.d("StoreActivity", "Fetched items from RoomDB: $it")
            }
        }
    }

    override fun onItemClick(position: Int) {
        val selectedItem = adapter.currentList[position]
        showPurchaseDialog(selectedItem)
    }

    private fun showPurchaseDialog(item: Item) {
        if (item.isPurchased) {
            runOnUiThread {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("구매 불가")
                    .setMessage("이미 구매한 아이템입니다.")
                    .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            return
        }
        else if (item.price > sharedPreferences.getInt("Potato", 0)) {
            runOnUiThread {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("구매 불가")
                    .setMessage("감자가 부족합니다.")
                    .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            return
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("구매 확인")
        builder.setMessage("정말 ${item.name}을(를) 구매하시겠습니까?")

        builder.setPositiveButton("예") { dialog, which ->
            purchaseItem(item.id)
            dialog.dismiss()
        }

        builder.setNegativeButton("아니오") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun purchaseItem(itemId: Int) {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val loginId = sharedPref.getString("login_id", null)
        if (loginId == null) {
            Log.e("PurchaseItem", "User not logged in")
            return
        }

        // Server DB update
        val request = PurchaseItemRequest(user_id = loginId, item_id = itemId)
        apiService.purchaseItem(request).enqueue(object : Callback<PurchaseItemResponse> {
            override fun onResponse(call: Call<PurchaseItemResponse>, response: Response<PurchaseItemResponse>) {
                if (response.isSuccessful && response.body()?.status == "Item purchased successfully") {
                    // Local DB update
                    CoroutineScope(Dispatchers.IO).launch {
                        val item = repository.getItemById(itemId)

                        item?.let {
                            val updatedItem = it.copy(isPurchased = true)
                            repository.update(updatedItem)
                            Log.d("StoreActivity", "Item ID $itemId has been purchased and updated")

                            val currentCount = sharedPreferences.getInt("Potato", 0)
                            val newCount = currentCount - item.price
                            val editor: SharedPreferences.Editor = sharedPreferences.edit()
                            editor.putInt("Potato", newCount)
                            editor.apply()
                            runOnUiThread {
                                potatoCount.text = "$newCount"
                            }
                        }
                    }
                } else {
                    Log.e("ServerResponse", "Failed to purchase item: ${response.body()?.error}")
                }
            }

            override fun onFailure(call: Call<PurchaseItemResponse>, t: Throwable) {
                Log.e("ServerResponse", "Error: ${t.message}")
            }
        })
    }
}
