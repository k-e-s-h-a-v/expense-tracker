package com.keshav.expensetracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keshav.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
  val searchQuery by viewModel.searchQuery.collectAsState()

  var showCreateCategoryDialog by remember { mutableStateOf(false) }
  var menuCategoryId by remember { mutableStateOf<String?>(null) }

  // Derive list of unique available merchants from current transactions
  val availableMerchants =
          remember(transactions) { transactions.mapNotNull { it.merchant }.distinct().toSet() }

  // Filter transactions based on selected senders and merchants
  val filteredTransactions =
          remember(transactions, selectedSenders, selectedMerchants) {
            transactions.filter { tx ->
              val senderMatch = selectedSenders.isEmpty() || selectedSenders.contains(tx.sender)
              val merchantMatch =
                      selectedMerchants.isEmpty() ||
                              (tx.merchant != null && selectedMerchants.contains(tx.merchant))
              senderMatch && merchantMatch
            }
          }

  // uiTransactions are further filtered by search, but do not impact the totalSpent calculation
  val uiTransactions =
          remember(filteredTransactions, searchQuery) {
            if (searchQuery.isBlank()) filteredTransactions
            else
                    filteredTransactions.filter {
                      it.body.contains(searchQuery, ignoreCase = true) ||
                              (it.merchant?.contains(searchQuery, ignoreCase = true) ?: false)
                    }
          }

  // Recalculate total spent from the active filtered transaction set (ignoring search)
  val totalSpent =
          remember(filteredTransactions) {
            filteredTransactions.filter { it.isDebit }.sumOf { it.amount }
          }

  var hasPermission by remember {
    mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
            ->
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
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              Text(
                      "Categories",
                      modifier = Modifier.padding(16.dp),
                      style = MaterialTheme.typography.titleSmall
              )

              categories.forEach { category ->
                var showLocalMenu by remember { mutableStateOf(false) }
                val isSelected = activeCategoryId == category.id

                Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
                  Surface(
                          shape = RoundedCornerShape(32.dp),
                          color =
                                  if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                  else androidx.compose.ui.graphics.Color.Transparent,
                          modifier =
                                  Modifier.fillMaxWidth()
                                          .height(56.dp)
                                          .combinedClickable(
                                                  onClick = {
                                                    viewModel.selectCategory(category.id)
                                                    scope.launch { drawerState.close() }
                                                  },
                                                  onLongClick = {
                                                    menuCategoryId = category.id
                                                    showLocalMenu = true
                                                  }
                                          )
                  ) {
                    Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                              text = category.name,
                              style = MaterialTheme.typography.labelLarge,
                              color =
                                      if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                      else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    CategoryDropdownMenu(
                            expanded = showLocalMenu,
                            onDismiss = { showLocalMenu = false },
                            onRename = {
                              showLocalMenu = false
                              menuCategoryId = category.id
                              showCreateCategoryDialog = true
                            },
                            onDuplicate = {
                              showLocalMenu = false
                              viewModel.duplicateCategory(context, category.id)
                            },
                            onDelete = {
                              showLocalMenu = false
                              viewModel.deleteCategory(context, category.id)
                            }
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.weight(1f))
            }
          }
  ) {
    Scaffold(
            topBar = {
              TopAppBar(
                      title = {
                        Text(
                                categories.find { it.id == activeCategoryId }?.name
                                        ?: "Expense Tracker"
                        )
                      },
                      navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                          Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                      },
                      actions = {
                        val isAnyFilterActive =
                                selectedDateMs != viewModel.defaultDateMs ||
                                        selectedSenders.isNotEmpty() ||
                                        selectedMerchants.isNotEmpty()

                        val isSaveEnabled =
                                if (activeCategoryId == null) {
                                  isAnyFilterActive
                                } else {
                                  val activeCat = categories.find { it.id == activeCategoryId }
                                  activeCat != null &&
                                          (activeCat.startDateMs != selectedDateMs ||
                                                  activeCat.selectedSenders != selectedSenders ||
                                                  activeCat.selectedMerchants != selectedMerchants)
                                }

                        if (isAnyFilterActive) {
                          var showTopBarMenu by remember { mutableStateOf(false) }
                          Box {
                            IconButton(
                                    onClick = {
                                      if (activeCategoryId != null) {
                                        showTopBarMenu = true
                                      } else {
                                        showCreateCategoryDialog = true
                                      }
                                    },
                                    enabled = isSaveEnabled || activeCategoryId != null
                            ) {
                              Icon(
                                      if (activeCategoryId != null) Icons.Default.MoreVert
                                      else Icons.Outlined.Save,
                                      contentDescription =
                                              if (activeCategoryId != null) "More options"
                                              else "Save Category"
                              )
                            }

                            if (activeCategoryId != null) {
                              CategoryDropdownMenu(
                                      expanded = showTopBarMenu,
                                      onDismiss = { showTopBarMenu = false },
                                      onRename = {
                                        showTopBarMenu = false
                                        menuCategoryId = activeCategoryId
                                        showCreateCategoryDialog = true
                                      },
                                      onDuplicate = {
                                        showTopBarMenu = false
                                        viewModel.duplicateCategory(context, activeCategoryId!!)
                                      },
                                      onDelete = {
                                        showTopBarMenu = false
                                        viewModel.deleteCategory(context, activeCategoryId!!)
                                      }
                              )
                            }
                          }
                        }
                      },
                      colors =
                              TopAppBarDefaults.topAppBarColors(
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

          CompactTextField(
                  value = searchQuery,
                  onValueChange = { viewModel.updateSearchQuery(it) },
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                  placeholder = "Search transactions...",
                  leadingIcon = {
                    Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline
                    )
                  },
                  trailingIcon =
                          if (searchQuery.isNotEmpty()) {
                            {
                              IconButton(
                                      onClick = { viewModel.updateSearchQuery("") },
                                      modifier = Modifier.size(28.dp)
                              ) {
                                Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp)
                                )
                              }
                            }
                          } else null
          )

          TransactionList(
                  transactions = uiTransactions,
                  onAssignMerchant = { tx, newName ->
                    viewModel.saveMerchantMapping(context, tx.id, newName)
                  },
                  modifier = Modifier.weight(1f)
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
    val activeCategory = categories.find { it.id == activeCategoryId }
    var categoryName by remember { mutableStateOf(activeCategory?.name ?: "") }
    val isDuplicate = categories.any { it.name == categoryName && it.id != activeCategoryId }

    AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            title = {
              Text(if (activeCategoryId == null) "Create Category" else "Update Category")
            },
            text = {
              Column {
                Text(
                        "This will save the current filters (Date, Senders, Merchants) as a category.",
                        style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isDuplicate,
                        supportingText = {
                          if (isDuplicate) {
                            Text(
                                    "Category name already exists",
                                    color = MaterialTheme.colorScheme.error
                            )
                          }
                        }
                )
              }
            },
            confirmButton = {
              val currentActiveId = activeCategoryId
              Button(
                      onClick = {
                        if (categoryName.isNotBlank() && !isDuplicate) {
                          if (currentActiveId == null) {
                            viewModel.saveCategory(context, categoryName)
                          } else {
                            viewModel.updateCategory(context, currentActiveId, categoryName)
                          }
                          showCreateCategoryDialog = false
                        }
                      },
                      enabled = categoryName.isNotBlank() && !isDuplicate
              ) { Text("Save") }
            },
            dismissButton = {
              TextButton(onClick = { showCreateCategoryDialog = false }) { Text("Cancel") }
            }
    )
  }
}

@Composable
fun CategoryDropdownMenu(
        expanded: Boolean,
        onDismiss: () -> Unit,
        onRename: () -> Unit,
        onDuplicate: () -> Unit,
        onDelete: () -> Unit
) {
  MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp))) {
    DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier =
                    Modifier.background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(24.dp)
                    )
    ) {
      Row(
              modifier = Modifier.padding(horizontal = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        PopoverIcon(Icons.Default.Edit, "Rename", onClick = onRename)
        PopoverIcon(Icons.Outlined.FileCopy, "Duplicate", onClick = onDuplicate)
        PopoverIcon(
                Icons.Outlined.Delete,
                "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
        )
      }
    }
  }
}

@Composable
fun PopoverIcon(
        icon: ImageVector,
        contentDescription: String,
        tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
        onClick: () -> Unit
) {
  IconButton(onClick = onClick) { Icon(icon, contentDescription = contentDescription, tint = tint) }
}
