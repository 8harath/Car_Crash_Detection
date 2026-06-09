package com.bharath.carcrashdetection.ui.publisher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bharath.carcrashdetection.R
import com.bharath.carcrashdetection.data.model.EmergencyContact
import com.bharath.carcrashdetection.data.model.MedicalProfile
import com.bharath.carcrashdetection.util.PermissionManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MedicalProfileEditorActivity : AppCompatActivity() {
    
    private val viewModel: MedicalProfileEditorViewModel by viewModels()
    private lateinit var emergencyContactsAdapter: EmergencyContactsAdapter
    private var currentPhotoPath: String? = null
    
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                loadProfilePhoto(path)
            }
        }
    }
    
    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { loadProfilePhotoFromUri(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_profile_editor)
        
        setupToolbar()
        setupEmergencyContactsRecyclerView()
        setupButtons()
        setupObservers()
        
        // Load existing profile if editing
        intent.getLongExtra("profile_id", -1).let { profileId ->
            if (profileId != -1L) {
                viewModel.loadProfile(profileId)
            }
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = "Medical Profile"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupEmergencyContactsRecyclerView() {
        emergencyContactsAdapter = EmergencyContactsAdapter(
            onContactClick = { contact -> showEditContactDialog(contact) },
            onContactDelete = { contact -> viewModel.removeEmergencyContact(contact) }
        )
        
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvEmergencyContacts).apply {
            layoutManager = LinearLayoutManager(this@MedicalProfileEditorActivity)
            adapter = emergencyContactsAdapter
        }
    }
    
    private fun setupButtons() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTakePhoto).setOnClickListener {
            if (PermissionManager.hasCameraPermissions(this)) {
                showPhotoOptionsDialog()
            } else {
                PermissionManager.requestCameraPermissions(this)
            }
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddContact).setOnClickListener {
            showAddContactDialog()
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            saveProfile()
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.profile.collect { profile ->
                profile?.let { populateFields(it) }
            }
        }
        
        lifecycleScope.launch {
            viewModel.emergencyContacts.collect { contacts ->
                emergencyContactsAdapter.updateContacts(contacts)
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveProfile).isEnabled = !isLoading
            }
        }
        
        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let { showError(it) }
            }
        }
        
        lifecycleScope.launch {
            viewModel.successMessage.collect { message ->
                message?.let { 
                    showSuccess(it)
                    finish()
                }
            }
        }
    }
    
    private fun populateFields(profile: MedicalProfile) {
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFullName).setText(profile.fullName)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDateOfBirth).setText(profile.dateOfBirth)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etHeight).setText(profile.height)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWeight).setText(profile.weight)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBloodType).setText(profile.bloodType)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAllergies).setText(profile.allergies)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedications).setText(profile.medications)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedicalConditions).setText(profile.medicalConditions)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etInsuranceInfo).setText(profile.insuranceInfo)
        findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbOrganDonor).isChecked = profile.organDonor
        
        // Load profile photo
        profile.photoPath?.let { path ->
            loadProfilePhoto(path)
            currentPhotoPath = path
        }
    }
    
    private fun showPhotoOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Profile Photo")
            .setItems(arrayOf("Take Photo", "Choose from Gallery")) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> chooseFromGallery()
                }
            }
            .show()
    }
    
    private fun takePhoto() {
        val photoFile = createImageFile()
        currentPhotoPath = photoFile.absolutePath
        
        val photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        
        takePictureLauncher.launch(photoUri)
    }
    
    private fun chooseFromGallery() {
        getContentLauncher.launch("image/*")
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PROFILE_${timeStamp}_"
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    private fun loadProfilePhoto(path: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(path)
            findViewById<android.widget.ImageView>(R.id.ivProfilePhoto).setImageBitmap(bitmap)
        } catch (e: Exception) {
            showError("Failed to load photo: ${e.message}")
        }
    }
    
    private fun loadProfilePhotoFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            findViewById<android.widget.ImageView>(R.id.ivProfilePhoto).setImageBitmap(bitmap)
            
            // Save the image to a file
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath
            
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            showError("Failed to load photo: ${e.message}")
        }
    }
    
    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emergency_contact, null)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etName)
        val etRelationship = dialogView.findViewById<android.widget.EditText>(R.id.etRelationship)
        val etPhone = dialogView.findViewById<android.widget.EditText>(R.id.etPhone)
        val etEmail = dialogView.findViewById<android.widget.EditText>(R.id.etEmail)
        val cbPrimary = dialogView.findViewById<android.widget.CheckBox>(R.id.cbPrimary)
        
        AlertDialog.Builder(this)
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val contact = EmergencyContact(
                    name = etName.text.toString(),
                    relationship = etRelationship.text.toString(),
                    phoneNumber = etPhone.text.toString(),
                    email = etEmail.text.toString().takeIf { it.isNotEmpty() },
                    isPrimary = cbPrimary.isChecked
                )
                viewModel.addEmergencyContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditContactDialog(contact: EmergencyContact) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emergency_contact, null)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etName)
        val etRelationship = dialogView.findViewById<android.widget.EditText>(R.id.etRelationship)
        val etPhone = dialogView.findViewById<android.widget.EditText>(R.id.etPhone)
        val etEmail = dialogView.findViewById<android.widget.EditText>(R.id.etEmail)
        val cbPrimary = dialogView.findViewById<android.widget.CheckBox>(R.id.cbPrimary)
        
        // Populate fields
        etName.setText(contact.name)
        etRelationship.setText(contact.relationship)
        etPhone.setText(contact.phoneNumber)
        etEmail.setText(contact.email)
        cbPrimary.isChecked = contact.isPrimary
        
        AlertDialog.Builder(this)
            .setTitle("Edit Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val updatedContact = contact.copy(
                    name = etName.text.toString(),
                    relationship = etRelationship.text.toString(),
                    phoneNumber = etPhone.text.toString(),
                    email = etEmail.text.toString().takeIf { it.isNotEmpty() },
                    isPrimary = cbPrimary.isChecked
                )
                viewModel.updateEmergencyContact(updatedContact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveProfile() {
        val profile = MedicalProfile(
            id = intent.getLongExtra("profile_id", 0),
            userId = 1L, // TODO: Get from user session
            fullName = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFullName).text.toString(),
            dateOfBirth = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDateOfBirth).text.toString(),
            height = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etHeight).text.toString(),
            weight = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWeight).text.toString(),
            bloodType = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBloodType).text.toString(),
            allergies = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAllergies).text.toString(),
            medications = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedications).text.toString(),
            medicalConditions = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedicalConditions).text.toString(),
            insuranceInfo = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etInsuranceInfo).text.toString(),
            organDonor = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbOrganDonor).isChecked,
            photoPath = currentPhotoPath
        )
        
        viewModel.saveProfile(profile)
    }
    
    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showSuccess(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
