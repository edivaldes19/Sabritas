package com.manuel.sabritas

import com.google.firebase.firestore.Exclude

data class Chips(
    @get:Exclude var id: String? = null,
    var brand: Int = 0,
    var flavorPresentation: String? = null,
    var grams: Int = 0,
    var existence: Int = 0,
    var priceToThePublic: Double = 0.0,
    var lastUpdate: Long = 0,
    var imagePath: String? = null,
    var providerId: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Chips
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}