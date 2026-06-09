package com.example.expensetracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.viewmodel.ExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseApp(viewModel: ExpenseViewModel = viewModel()) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()
    val allSenders by viewModel.allSenders.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeCategoryId by viewModel.activeCategoryId.collectAsState()
    val selectedDateMs by viewModel.selectedDateMs.collectAsState()
    val selectedSenders by viewModel.selectedSenders.collectAsState()
    val selectedMerchants by viewModel.selectedMerchants.collectAsState()
    
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    // Derive list of unique available merchants from current transactions
    val availableMerchants = remember(transactions) {
        transactions.mapNotNull { it.merchant }.distinct().toSet()
    }

    // Filter transactions based on selected senders and merchants
    val filteredTransactions = remember(transactions, selectedSenders, selectedMerchants) {
        transactions.filter { tx ->
            val senderMatch = selectedSenders.isEmpty() || selectedSenders.contains(tx.sender)
            val merchantMatch = selectedMerchants.isEmpty() || (tx.merchant != null && selectedMerchants.contains(tx.merchant))
            senderMatch && merchantMatch
        }
    }

    // Recalculate total spent from the active filtered transaction set
    val totalSpent = remember(filteredTransactions) {
        filteredTransactions.filter { it.isDebit }.sumOf { it.amount }
    }
    
    var hasPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.init(context)
        } else {
            Toast.makeText(context, "SMS Permission is required", Toast.LENGTH_LONG).show()
        }
    }

    // Initial load
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.init(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("All Transactions") },
                    selected = activeCategoryId == null,
                    onClick = { 
                        viewModel.selectCategory(null)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Categories", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                
                categories.forEach { category ->
                    NavigationDrawerItem(
                        label = { Text(category.name) },
                        selected = activeCategoryId == category.id,
                        onClick = {
                            viewModel.selectCategory(category.id)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { 
                        showCreateCategoryDialog = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Category")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(categories.find { it.id == activeCategoryId }?.name ?: "Expense Tracker") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (hasPermission) {
                    DashboardHeader(
                        totalSpent = totalSpent, 
                        selectedDateMs = selectedDateMs,
                        onDateSelected = { newDateMs -> viewModel.updateStartDate(context, newDateMs) }
                    )
                    
                    FilterSection(
                        availableSenders = allSenders,
                        selectedSenders = selectedSenders,
                        onToggleSender = { viewModel.toggleSenderFilter(it) },
                        availableMerchants = availableMerchants,
                        selectedMerchants = selectedMerchants,
                        onToggleMerchant = { viewModel.toggleMerchantFilter(it) },
                        onClearFilters = { viewModel.clearFilters() }
                    )

                    TransactionList(
                        transactions = filteredTransactions,
                        onAssignMerchant = { tx, newName ->
                            viewModel.saveMerchantMapping(context, tx.sender, newName)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                            Text("Grant SMS Permission")
                        }
                    }
                }
            }
        }
    }

    if (showCreateCategoryDialog) {
        var categoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            title = { Text("Create Category") },
            text = {
                Column {
                    Text("This will save the current filters (Date, Senders, Merchants) as a category.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (categoryName.isNotBlank()) {
                            viewModel.saveCategory(context, categoryName)
                            showCreateCategoryDialog = false
                        }
                    },
                    enabled = categoryName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
