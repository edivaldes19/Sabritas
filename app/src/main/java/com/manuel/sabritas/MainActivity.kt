package com.manuel.sabritas

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.ktx.storage
import com.manuel.sabritas.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), OnChipsListener, OnChipsSelected,
    ConnectionReceiver.ReceiverListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var chipsAdapter: ChipsAdapter
    private lateinit var listenerRegistration: ListenerRegistration
    private var chipsSelected: Chips? = null
    private var chipsList = mutableListOf<Chips>()
    private val aValues: Array<String> by lazy {
        resources.getStringArray(R.array.names_value)
    }
    private val aKeys: Array<Int> by lazy {
        resources.getIntArray(R.array.names_key).toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Sabritas)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupButtons()
        checkInternetConnection()
    }

    override fun onResume() {
        super.onResume()
        setupFirestoreInRealtime()
    }

    override fun onPause() {
        super.onPause()
        listenerRegistration.remove()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        val menuItem = menu?.findItem(R.id.action_search)
        val searchView = menuItem?.actionView as SearchView
        searchView.queryHint = getString(R.string.search_by_name)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredList = mutableListOf<Chips>()
                var brand = ""
                for (chips in chipsList) {
                    val index = aKeys.indexOf(chips.brand)
                    if (index != -1) {
                        brand = aValues[index]
                    }
                    if (newText!! in brand) {
                        filteredList.add(chips)
                    }
                }
                chipsAdapter.updateList(filteredList)
                if (filteredList.isNullOrEmpty()) {
                    binding.tvWithoutResults.visibility = View.VISIBLE
                    binding.tvTotalElements.visibility = View.GONE
                } else {
                    binding.tvWithoutResults.visibility = View.GONE
                    updateTheTotalNumberOfItems(getString(R.string.items_found), filteredList.size)
                }
                if (newText.isNullOrEmpty()) {
                    updateTheTotalNumberOfItems(getString(R.string.total_items), filteredList.size)
                }
                return false
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onClick(chips: Chips) {
        chipsSelected = chips
        AddDialogFragment().show(supportFragmentManager, AddDialogFragment::class.java.simpleName)
    }

    override fun onClickInDelete(chips: Chips) {
        var brand = ""
        val index = aKeys.indexOf(chips.brand)
        if (index != -1) {
            brand = aValues[index]
        }
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.delete))
            .setMessage("¿${getString(R.string.are_you_sure_of)} ${getString(R.string.delete).lowercase()} $brand ${chips.flavorPresentation}")
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                chips.id?.let { id ->
                    chips.imagePath?.let { url ->
                        try {
                            val reference = Firebase.storage.getReferenceFromUrl(url)
                            reference.delete().addOnSuccessListener { deleteChipsInFirestore(id) }
                                .addOnFailureListener { exception ->
                                    if ((exception as StorageException).errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                                        deleteChipsInFirestore(id)
                                    } else {
                                        Snackbar.make(
                                            binding.root,
                                            getString(R.string.failed_to_delete_image),
                                            Snackbar.LENGTH_SHORT
                                        ).setTextColor(Color.YELLOW).show()
                                    }
                                }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            deleteChipsInFirestore(id)
                        }
                    }
                }
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    override fun getChipsSelected() = chipsSelected
    override fun onNetworkChange(isConnected: Boolean) {
        showNetworkErrorSnackBar(isConnected)
    }

    private fun setupRecyclerView() {
        chipsAdapter = ChipsAdapter(chipsList, this)
        binding.rvList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.chipsAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy < 0) {
                        binding.eFabAdd.show()
                    } else if (dy > 0) {
                        binding.eFabAdd.hide()
                    }
                }
            })
        }
    }

    private fun setupButtons() {
        binding.eFabAdd.setOnClickListener {
            chipsSelected = null
            AddDialogFragment().show(
                supportFragmentManager,
                AddDialogFragment::class.java.simpleName
            )
        }
    }

    private fun checkInternetConnection() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(Constants.ACTION_INTENT)
        registerReceiver(ConnectionReceiver(), intentFilter)
        ConnectionReceiver.receiverListener = this
        val manager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo
        val isConnected = networkInfo != null && networkInfo.isConnectedOrConnecting
        showNetworkErrorSnackBar(isConnected)
    }

    private fun showNetworkErrorSnackBar(isConnected: Boolean) {
        if (isConnected) {
            Snackbar.make(
                binding.root,
                getString(R.string.you_have_connection),
                Snackbar.LENGTH_SHORT
            ).setTextColor(Color.GREEN).show()
        } else {
            Snackbar.make(
                binding.root,
                getString(R.string.no_network_connection),
                Snackbar.LENGTH_INDEFINITE
            ).setTextColor(Color.WHITE)
                .setAction(getString(R.string.go_to_settings)) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                .show()
        }
    }

    private fun deleteChipsInFirestore(chipsId: String) {
        val db = Firebase.firestore
        val reference = db.collection(Constants.COLL_CHIPS)
        reference.document(chipsId).delete().addOnSuccessListener {
            Toast.makeText(this, getString(R.string.chips_removed), Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Snackbar.make(
                binding.root,
                getString(R.string.failed_to_remove_chips),
                Snackbar.LENGTH_SHORT
            ).setTextColor(Color.YELLOW).show()
        }
    }

    private fun setupFirestoreInRealtime() {
        val db = Firebase.firestore
        val reference = db.collection(Constants.COLL_CHIPS)
            .orderBy(Constants.PROP_LAST_UPDATE, Query.Direction.DESCENDING)
        listenerRegistration = reference.addSnapshotListener { querySnapshot, error ->
            if (error != null) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.failed_to_query_the_data),
                    Snackbar.LENGTH_SHORT
                ).setTextColor(Color.YELLOW).show()
                return@addSnapshotListener
            }
            for (documentChange in querySnapshot!!.documentChanges) {
                val chips = documentChange.document.toObject(Chips::class.java)
                chips.id = documentChange.document.id
                when (documentChange.type) {
                    DocumentChange.Type.ADDED -> {
                        chipsAdapter.add(chips)
                        updateTheTotalNumberOfItems(
                            getString(R.string.total_items),
                            chipsAdapter.itemCount
                        )
                    }
                    DocumentChange.Type.MODIFIED -> {
                        chipsAdapter.update(chips)
                        updateTheTotalNumberOfItems(
                            getString(R.string.total_items),
                            chipsAdapter.itemCount
                        )
                    }
                    DocumentChange.Type.REMOVED -> {
                        chipsAdapter.delete(chips)
                        updateTheTotalNumberOfItems(
                            getString(R.string.total_items),
                            chipsAdapter.itemCount
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateTheTotalNumberOfItems(s: String, size: Int) {
        binding.tvTotalElements.apply {
            visibility = View.VISIBLE
            text = "$s $size"
        }
    }
}