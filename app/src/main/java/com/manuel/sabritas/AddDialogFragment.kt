package com.manuel.sabritas

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.manuel.sabritas.databinding.FragmentDialogAddBinding
import java.io.ByteArrayOutputStream
import java.util.*

class AddDialogFragment : DialogFragment(), DialogInterface.OnShowListener,
    AdapterView.OnItemClickListener {
    private var binding: FragmentDialogAddBinding? = null
    private var fabAdd: FloatingActionButton? = null
    private var fabCancel: FloatingActionButton? = null
    private var chips: Chips? = null
    private var photoSelectedUri: Uri? = null
    private var position = 0
    private val snackBar: Snackbar by lazy {
        Snackbar.make(binding!!.root, "", Snackbar.LENGTH_SHORT).setTextColor(Color.YELLOW)
    }
    private val aValues: Array<String> by lazy {
        resources.getStringArray(R.array.brand_values)
    }
    private val aKeys: Array<Int> by lazy {
        resources.getIntArray(R.array.brand_keys).toTypedArray()
    }
    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            if (activityResult.resultCode == Activity.RESULT_OK) {
                photoSelectedUri = activityResult.data?.data
                binding?.let { view ->
                    Glide.with(this).load(photoSelectedUri).diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(view.imgChipsPreview)
                }
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        activity?.let { activity ->
            binding = FragmentDialogAddBinding.inflate(LayoutInflater.from(context))
            binding?.let { view ->
                fabAdd = view.fabAdd
                fabCancel = view.fabCancel
                TextWatchers.validateFieldsAsYouType(
                    activity,
                    fabAdd!!,
                    view.etFlavorPresentation,
                    view.etGrams,
                    view.etExistence,
                    view.etPrice
                )
                val arrayAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    aValues
                )
                view.atvBrand.setAdapter(arrayAdapter)
                view.atvBrand.onItemClickListener = this
                val builder =
                    MaterialAlertDialogBuilder(activity).setTitle(getString(R.string.add_chips))
                        .setView(view.root)
                val dialog = builder.create()
                dialog.setOnShowListener(this)
                return dialog
            }
        }
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onShow(dialogInterface: DialogInterface?) {
        initChips()
        setupButtons()
        val dialog = dialog as? AlertDialog
        dialog?.let { alertDialog ->
            alertDialog.setCanceledOnTouchOutside(false)
            chips?.let {
                fabAdd?.setImageResource(R.drawable.ic_edit)
            }
            fabAdd?.setOnClickListener {
                binding?.let { view ->
                    enableAllInterface(false)
                    uploadCompressedImage(chips?.id, chips?.imagePath) { eventPost ->
                        if (eventPost.isSuccess) {
                            if (!theyAreEmpty()) {
                                if (chips == null) {
                                    val chips1 = Chips(
                                        brand = position,
                                        flavorPresentation = view.etFlavorPresentation.text.toString()
                                            .trim(),
                                        grams = view.etGrams.text.toString().trim().toInt(),
                                        existence = view.etExistence.text.toString().trim().toInt(),
                                        priceToThePublic = view.etPrice.text.toString().trim()
                                            .toDouble(),
                                        lastUpdate = Date().time,
                                        imagePath = eventPost.imagePath,
                                        providerId = eventPost.providerId
                                    )
                                    save(chips1, eventPost.documentId!!)
                                } else {
                                    chips?.apply {
                                        brand = position
                                        flavorPresentation =
                                            view.etFlavorPresentation.text.toString().trim()
                                        grams = view.etGrams.text.toString().trim().toInt()
                                        existence = view.etExistence.text.toString().trim().toInt()
                                        priceToThePublic =
                                            view.etPrice.text.toString().trim().toDouble()
                                        lastUpdate = Date().time
                                        imagePath = eventPost.imagePath
                                        update(this)
                                    }
                                }
                            } else {
                                enableAllInterface(true)
                                snackBar.apply {
                                    setText(getString(R.string.there_are_still_empty_fields))
                                    show()
                                }
                            }
                        }
                    }
                }
            }
            fabCancel?.setOnClickListener {
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        position = p2
    }

    @SuppressLint("SetTextI18n")
    private fun initChips() {
        chips = (activity as? OnChipsSelected)?.getChipsSelected()
        chips?.let { chips1 ->
            binding?.let { view ->
                dialog?.setTitle(getString(R.string.update_chips))
                position = chips1.brand
                val index = aKeys.indexOf(chips1.brand)
                if (index != -1) {
                    view.atvBrand.setText(aValues[index], false)
                } else {
                    view.atvBrand.setText(getString(R.string.unknown), false)
                }
                view.etFlavorPresentation.setText(chips1.flavorPresentation)
                view.etGrams.setText(chips1.grams.toString())
                view.etPrice.setText(chips1.priceToThePublic.toString())
                view.etExistence.setText(chips1.existence.toString())
                view.etPrice.setText(chips1.priceToThePublic.toString())
                view.tvLastModification.text = "${getString(R.string.last_update)}: ${
                    TimestampToText.getTimeAgo(chips1.lastUpdate).lowercase()
                }."
                Glide.with(this).load(chips1.imagePath).diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(view.imgChipsPreview)
            }
        }
    }

    private fun setupButtons() {
        binding?.let { view ->
            view.imgChips.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                resultLauncher.launch(intent)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun uploadCompressedImage(
        chipsId: String?,
        imagePath: String?,
        callback: (EventPost) -> Unit
    ) {
        val eventPost = EventPost()
        imagePath?.let { path -> eventPost.imagePath = path }
        eventPost.documentId =
            chipsId ?: Firebase.firestore.collection(Constants.COLL_CHIPS).document().id
        FirebaseAuth.getInstance().currentUser?.let { user ->
            val reference = Firebase.storage.reference.child(eventPost.documentId!!)
            eventPost.providerId = user.uid
            if (photoSelectedUri == null) {
                eventPost.isSuccess = true
                callback(eventPost)
            } else {
                binding?.let { view ->
                    getBitmapFromUri(photoSelectedUri!!)?.let { bitmap ->
                        view.progressBar.visibility = View.VISIBLE
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                        reference.putBytes(stream.toByteArray())
                            .addOnProgressListener { taskSnapshot ->
                                val progress =
                                    (100 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                                taskSnapshot.run {
                                    view.progressBar.progress = progress
                                    view.tvProgress.text =
                                        "${getString(R.string.uploading_image)} ${
                                            String.format(
                                                "%s%%",
                                                progress
                                            )
                                        }"
                                }
                            }.addOnSuccessListener { taskSnapshot ->
                                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                                    eventPost.isSuccess = true
                                    eventPost.imagePath = downloadUrl.toString()
                                    callback(eventPost)
                                }
                            }.addOnFailureListener {
                                snackBar.apply {
                                    setText(getString(R.string.error_uploading_image))
                                    show()
                                }
                                enableAllInterface(true)
                                eventPost.isSuccess = false
                                callback(eventPost)
                            }
                    }
                }
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        activity?.let { fragmentActivity ->
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(fragmentActivity.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(fragmentActivity.contentResolver, uri)
            }
            return getResizedImage(bitmap)
        }
        return null
    }

    private fun getResizedImage(image: Bitmap): Bitmap {
        var width = image.width
        var height = image.height
        if (width <= 320 && height <= 320) {
            return image
        }
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = 320
            height = (width / bitmapRatio).toInt()
        } else {
            height = 320
            width = (height / bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    private fun save(chips: Chips, documentId: String) {
        val db = Firebase.firestore
        db.collection(Constants.COLL_CHIPS).document(documentId).set(chips).addOnSuccessListener {
            Toast.makeText(activity, getString(R.string.chips_added), Toast.LENGTH_SHORT).show()
            dismiss()
        }.addOnFailureListener {
            snackBar.apply {
                setText(getString(R.string.failed_to_add_chips))
                show()
            }
        }.addOnCompleteListener {
            enableAllInterface(true)
            binding?.progressBar?.visibility = View.INVISIBLE
        }
    }

    private fun update(chips: Chips) {
        val db = Firebase.firestore
        chips.id?.let { id ->
            db.collection(Constants.COLL_CHIPS).document(id).set(chips).addOnSuccessListener {
                Toast.makeText(activity, getString(R.string.chips_updated), Toast.LENGTH_SHORT)
                    .show()
                dismiss()
            }.addOnFailureListener {
                snackBar.apply {
                    setText(getString(R.string.failed_to_update_chips))
                    show()
                }
            }.addOnCompleteListener {
                enableAllInterface(true)
                binding?.progressBar?.visibility = View.INVISIBLE
            }
        }
    }

    private fun enableAllInterface(enable: Boolean) {
        fabAdd?.isEnabled = enable
        fabCancel?.isEnabled = enable
        binding?.let { view ->
            with(view) {
                atvBrand.isEnabled = enable
                etFlavorPresentation.isEnabled = enable
                etGrams.isEnabled = enable
                etExistence.isEnabled = enable
                etPrice.isEnabled = enable
                progressBar.visibility = if (enable) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }
                tvProgress.visibility = if (enable) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }
            }
        }
    }

    private fun theyAreEmpty(): Boolean {
        binding?.let { view ->
            with(view) {
                return atvBrand.text.isNullOrEmpty() || etFlavorPresentation.text.isNullOrEmpty() || etGrams.text.isNullOrEmpty() || etExistence.text.isNullOrEmpty() || etPrice.text.isNullOrEmpty()
            }
        }
        return false
    }
}