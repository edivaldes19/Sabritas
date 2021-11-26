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
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
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
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    private lateinit var chipsAdapter: ChipsAdapter
    private var chipsSelected: Chips? = null
    private var chipsList = mutableListOf<Chips>()
    private var listenerRegistration: ListenerRegistration? = null
    private val aValues: Array<String> by lazy {
        resources.getStringArray(R.array.brand_values)
    }
    private val aKeys: Array<Int> by lazy {
        resources.getIntArray(R.array.brand_keys).toTypedArray()
    }
    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            val response = IdpResponse.fromResultIntent(activityResult.data)
            if (activityResult.resultCode == RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    Toast.makeText(
                        this,
                        "${getString(R.string.welcome)} ${user.displayName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                if (response == null) {
                    Toast.makeText(this, getString(R.string.see_you_soon), Toast.LENGTH_SHORT)
                        .show()
                    finishAffinity()
                } else {
                    response.error?.let { exception ->
                        if (exception.errorCode == ErrorCodes.NO_NETWORK) {
                            Snackbar.make(
                                binding.root,
                                "${getString(R.string.error_code)}: ${exception.errorCode}",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } else {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.no_network_connection),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Sabritas)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAuth()
        setupRecyclerView()
        setupButtons()
        checkInternetConnection()
    }

    override fun onResume() {
        super.onResume()
        firebaseAuth.addAuthStateListener(authStateListener)
        setupFirestoreInRealtime()
    }

    override fun onPause() {
        super.onPause()
        firebaseAuth.removeAuthStateListener(authStateListener)
        listenerRegistration?.remove()
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
                    if (newText!!.lowercase() in brand.lowercase()) {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sign_out) {
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.sign_off))
                .setMessage(getString(R.string.are_you_sure_to_take_this_action))
                .setPositiveButton(getString(R.string.sign_off)) { _, _ ->
                    AuthUI.getInstance().signOut(this).addOnSuccessListener {
                        Toast.makeText(
                            this,
                            getString(R.string.you_have_logged_out),
                            Toast.LENGTH_SHORT
                        ).show()
                    }.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            binding.tvTotalElements.visibility = View.GONE
                            binding.rvList.visibility = View.GONE
                            binding.eFabAdd.hide()
                            binding.llProgress.visibility = View.VISIBLE
                        } else {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.failed_to_log_out),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
        return super.onOptionsItemSelected(item)
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
            .setMessage("¿${getString(R.string.are_you_sure_of)} ${getString(R.string.delete).lowercase()} $brand ${chips.flavorPresentation}?")
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
    override fun onNetworkChange(isConnected: Boolean) = showNetworkErrorSnackBar(isConnected)
    private fun setupAuth() {
        firebaseAuth = FirebaseAuth.getInstance()
        authStateListener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser != null) {
                supportActionBar?.title = auth.currentUser?.displayName
                binding.tvTotalElements.visibility = View.VISIBLE
                binding.rvList.visibility = View.VISIBLE
                binding.eFabAdd.show()
                binding.llProgress.visibility = View.GONE
            } else {
                val providers = arrayListOf(
                    AuthUI.IdpConfig.EmailBuilder().build(),
                    AuthUI.IdpConfig.GoogleBuilder().build()
                )
                val loginView = AuthMethodPickerLayout.Builder(R.layout.view_login)
                    .setEmailButtonId(R.id.btnEmail).setGoogleButtonId(R.id.btnGoogle)
                    .setTosAndPrivacyPolicyId(R.id.tvTermsAndConditions).build()
                authLauncher.launch(
                    AuthUI.getInstance().createSignInIntentBuilder()
                        .setAvailableProviders(providers).setIsSmartLockEnabled(false)
                        .setTosAndPrivacyPolicyUrls(
                            Constants.TERMS_AND_CONDITIONS,
                            Constants.PRIVACY_POLICY
                        ).setAuthMethodPickerLayout(loginView).setTheme(R.style.LoginTheme).build()
                )
            }
        }
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
        if (!isConnected) {
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
        FirebaseAuth.getInstance().currentUser?.let { provider ->
            val db = Firebase.firestore
            val reference = db.collection(Constants.COLL_CHIPS)
                .whereEqualTo(Constants.PROP_PROVIDER_ID, provider.uid)
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
    }

    @SuppressLint("SetTextI18n")
    private fun updateTheTotalNumberOfItems(tag: String, size: Int) {
        binding.tvTotalElements.apply {
            visibility = View.VISIBLE
            text = "$tag $size"
        }
    }
}