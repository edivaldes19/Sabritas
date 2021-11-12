package com.manuel.sabritas

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.manuel.sabritas.databinding.ItemChipsBinding

class ChipsAdapter(
    private var chipsList: MutableList<Chips>,
    private val listener: OnChipsListener
) : RecyclerView.Adapter<ChipsAdapter.ViewHolder>() {
    private lateinit var context: Context
    private val aValues: Array<String> by lazy {
        context.resources.getStringArray(R.array.names_value)
    }
    private val aKeys: Array<Int> by lazy {
        context.resources.getIntArray(R.array.names_key).toTypedArray()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_chips, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.root.animation =
            AnimationUtils.loadAnimation(context, R.anim.fade_transition)
        val chips = chipsList[position]
        holder.setListener(chips)
        val index = aKeys.indexOf(chips.brand)
        if (index != -1) {
            holder.binding.tvBrand.text = aValues[index]
        } else {
            holder.binding.tvBrand.text = context.getText(R.string.unknown)
        }
        holder.binding.tvPresentation.text = chips.flavorPresentation
        holder.binding.tvGrams.text =
            "${chips.grams} ${context.getString(R.string.grams)}".lowercase()
        holder.binding.tvExistence.text =
            "${chips.existence} ${context.getString(R.string.in_existence)}"
        holder.binding.tvPrice.text =
            "${context.getString(R.string.price_to_the_public)}: $${chips.priceToThePublic} MXN"
        holder.binding.tvLastUpdate.text = "${context.getString(R.string.last_update)}: ${
            TimestampToText.getTimeAgo(chips.lastUpdate).lowercase()
        }"
        Glide.with(context).load(chips.imagePath).diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_cloud_download).error(R.drawable.ic_broken_image)
            .into(holder.binding.imgPackage)
    }

    override fun getItemCount() = chipsList.size
    fun add(chips: Chips) {
        if (!chipsList.contains(chips)) {
            chipsList.add(chips)
            notifyItemInserted(chipsList.size - 1)
        } else {
            update(chips)
        }
    }

    fun update(chips: Chips) {
        val index = chipsList.indexOf(chips)
        if (index != -1) {
            chipsList[index] = chips
            notifyItemChanged(index)
        }
    }

    fun delete(chips: Chips) {
        val index = chipsList.indexOf(chips)
        if (index != -1) {
            chipsList.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(list: MutableList<Chips>) {
        chipsList = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val binding = ItemChipsBinding.bind(view)
        fun setListener(chips: Chips) {
            binding.root.setOnClickListener {
                listener.onClick(chips)
            }
            binding.imgDelete.setOnClickListener {
                listener.onClickInDelete(chips)
            }
        }
    }
}