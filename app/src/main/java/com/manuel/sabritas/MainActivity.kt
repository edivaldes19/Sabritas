package com.manuel.sabritas

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
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

class MainActivity : AppCompatActivity(), OnChipsListener, OnChipsSelected {
    private lateinit var binding: ActivityMainBinding
    private lateinit var chipsAdapter: ChipsAdapter
    private lateinit var listenerRegistration: ListenerRegistration
    private var chipsSelected: Chips? = null
    private var chipsList = mutableListOf<Chips>()
    private val snackBar: Snackbar by lazy {
        Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Sabritas)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupButtons()
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
                val temporaryList = mutableListOf<Chips>()
                for (chips in chipsList) {
                    if (newText!! in chips.brand.toString()) {
                        temporaryList.add(chips)
                    }
                }
                chipsAdapter.updateList(temporaryList)
                if (temporaryList.isNullOrEmpty()) {
                    binding.tvWithoutResults.visibility = View.VISIBLE
                } else {
                    binding.tvWithoutResults.visibility = View.GONE
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
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.delete))
            .setMessage("¿${getString(R.string.are_you_sure_of)} ${getString(R.string.delete).lowercase()} ${chips.brand} ${chips.flavorPresentation}")
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
                                        snackBar.apply {
                                            setText(getString(R.string.failed_to_delete_image))
                                            show()
                                        }
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

    override fun getChipsSelected(): Chips? = chipsSelected
    private fun setupRecyclerView() {
        chipsAdapter = ChipsAdapter(chipsList, this)
        binding.rvList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.chipsAdapter
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

    private fun deleteChipsInFirestore(chipsId: String) {
        val db = Firebase.firestore
        val reference = db.collection(Constants.COLL_CHIPS)
        reference.document(chipsId).delete().addOnFailureListener {
            snackBar.apply {
                setText(getString(R.string.failed_to_remove_chips))
                show()
            }
        }
    }

    private fun setupFirestoreInRealtime() {
        val db = Firebase.firestore
        val reference = db.collection(Constants.COLL_CHIPS)
            .orderBy(Constants.PROP_LAST_UPDATE, Query.Direction.DESCENDING)
        listenerRegistration = reference.addSnapshotListener { querySnapshot, error ->
            if (error != null) {
                snackBar.apply {
                    setText(getString(R.string.failed_to_query_the_data))
                    show()
                }
                return@addSnapshotListener
            }
            for (documentChange in querySnapshot!!.documentChanges) {
                val chips = documentChange.document.toObject(Chips::class.java)
                chips.id = documentChange.document.id
                when (documentChange.type) {
                    DocumentChange.Type.ADDED -> chipsAdapter.add(chips)
                    DocumentChange.Type.MODIFIED -> chipsAdapter.update(chips)
                    DocumentChange.Type.REMOVED -> chipsAdapter.delete(chips)
                }
            }
        }
    }
}