package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Structure Definitions matching database states
enum class Screen {
  TERMS,
  LOGIN,
  MAIN
}

enum class Tab {
  DASHBOARD,
  INTEL,
  SENSORS,
  LOGS
}

enum class Role {
  OFICIAL,
  SUPERVISOR,
  COORDINADOR,
  SEP
}

enum class IngressMode {
  HUMANOS,
  VEHICULOS
}

data class SystemNotification(
  val title: String,
  val message: String,
  val type: NotificationType
)

enum class NotificationType {
  SUCCESS,
  DANGER,
  INFO
}

data class VerificationRequest(
  val id: Int,
  val hora: String,
  val cedula: String,
  val contratista: String,
  val motivo: String,
  val supervisor: String,
  var valSeg: Boolean,
  var visCoord: Boolean,
  var aprSep: Boolean,
  val espera: String
)

data class CompanionGPS(
  val id: String,
  val name: String,
  val x: Float, // 0 to 100
  val y: Float, // 0 to 100
  val status: String
)

data class NovedadLog(
  val loc: String,
  val des: String,
  val h: String
)

// Main Component Activity
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        TacticalCoreApp()
      }
    }
  }
}

// Tactical State Container (ViewModel pattern adapted for robust local session state)
@Composable
fun rememberTacticalState(): TacticalState {
  val context = LocalContext.current
  return remember { TacticalState() }
}

class TacticalState {
  var currentScreen by mutableStateOf(Screen.TERMS)
  var currentTab by mutableStateOf(Tab.DASHBOARD)
  var activeRole by mutableStateOf<Role?>(null)
  var activeSede by mutableStateOf("")
  var selectedRolePending by mutableStateOf<Role?>(null)

  // Auth Dialog state
  var isAuthModalVisible by mutableStateOf(false)
  var pinInput by mutableStateOf("")
  var isSedeModalVisible by mutableStateOf(false)

  // Form states - Humanos
  var formName by mutableStateOf("")
  var formLastName by mutableStateOf("")
  var formCompany by mutableStateOf("")
  var formReason by mutableStateOf("")
  var formAuthorizedBy by mutableStateOf("")
  var formTimeIn by mutableStateOf("04:20")
  var formTimeOutEstimate by mutableStateOf("")

  // Form states - Vehiculos
  var formVehicleType by mutableStateOf("Gandola Chuto Plano")
  var formPlate by mutableStateOf("")
  var formWeight by mutableStateOf("")
  var formDestination by mutableStateOf("Almacén de Materia Prima")

  var oficialSubMode by mutableStateOf(IngressMode.HUMANOS)

  // Quick Action Drawer state
  var isQuickActionsOpen by mutableStateOf(false)

  // Toast System state
  var activeNotification by mutableStateOf<SystemNotification?>(null)
  private var notificationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var notificationJob: Job? = null

  // Interactive Live data fields
  val supervisorQueue = mutableStateListOf(
    VerificationRequest(1, "04:12", "V-18.442.102", "Alimentos Polar S.A.", "Despacho Harina PAN", "Sup. J. Castro", valSeg = true, visCoord = true, aprSep = false, espera = "8 min"),
    VerificationRequest(2, "04:18", "V-22.991.405", "Suministros Turmero", "Mantenimiento Caldera", "Sup. J. Castro", valSeg = true, visCoord = false, aprSep = false, espera = "2 min")
  )

  val companionList = listOf(
    CompanionGPS("INT-701", "M. Blanco (Oficial 01)", 30f, 45f, "Patrullaje Patio 2"),
    CompanionGPS("INT-702", "J. Delgado (Oficial 04)", 75f, 20f, "Resguardo Puerta A"),
    CompanionGPS("INT-703", "K. Alvarez (Oficial 09)", 50f, 80f, "Inspección Sanitario")
  )

  val droneLogs = mutableStateListOf(
    "DRN-342: Transmitiendo telemetría térmica ok.",
    "DRN-342: Escaneando cuadrante Caldera.",
    "DRN-342: Ausencia de anomalías críticas de calor."
  )

  val baseNovedades = mutableStateListOf(
    NovedadLog("Caldera 02", "Caída de presión momentánea, solventado.", "03:11"),
    NovedadLog("Patio de Gandolas", "Ingreso ordenado de 3 unidades pesadas.", "03:40"),
    NovedadLog("Recepción Central", "Relevo de guardia sin novedades físicas.", "04:00")
  )

  // Dedicated interactive technical console log lists (For estás pendiente de cada dato de consola y ver error)
  val consoleLogs = mutableStateListOf(
    "[SYSTEM INITIALIZATION] ... STATUS ACTIVE",
    "[SECURITY INTEGRITY] SENTINEL MODULES LOADED S-777",
    "[IP ASSIGNMENT] DHCP ALLOCATION SECURE 10.0.0.12",
    "[GPS TUNNEL] ENLACE DE CONFIANZA EN CONEXION"
  )

  fun triggerNotification(title: String, msg: String, type: NotificationType) {
    notificationJob?.cancel()
    activeNotification = SystemNotification(title, msg, type)
    addConsoleLog("NOTIFICACIÓN [${type.name}]: $title - $msg")
    notificationJob = notificationScope.launch {
      delay(3500)
      activeNotification = null
    }
  }

  fun addConsoleLog(message: String) {
    val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    consoleLogs.add(0, "[$currentTime] $message")
  }
}

// Global Core UI
@Composable
fun TacticalCoreApp() {
  val state = rememberTacticalState()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(BGDeep)
      .drawBehind {
        // Aesthetic dynamic technical grid lines in background
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        val gridSpacing = 60.dp.toPx()
        
        // Vertical lines
        var x = 0f
        while (x < size.width) {
          drawLine(
            color = NeonCyan.copy(alpha = 0.03f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
            pathEffect = pathEffect
          )
          x += gridSpacing
        }
        
        // Horizontal lines
        var y = 0f
        while (y < size.height) {
          drawLine(
            color = NeonCyan.copy(alpha = 0.03f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
            pathEffect = pathEffect
          )
          y += gridSpacing
        }
      }
  ) {
    when (state.currentScreen) {
      Screen.TERMS -> TermsAndPrivacyScreen(state)
      Screen.LOGIN -> OperatorLoginScreen(state)
      Screen.MAIN -> MainDashboardScreen(state)
    }

    // High fidelity active top Floating Notification
    AnimatedVisibility(
      visible = state.activeNotification != null,
      enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
      exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 48.dp)
        .statusBarsPadding()
        .zIndex(999f)
    ) {
      state.activeNotification?.let { notif ->
        NotificationBanner(notif)
      }
    }
  }
}

// SCREEN 1: TERMS AND CONDITIONS
@Composable
fun TermsAndPrivacyScreen(state: TacticalState) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.Center)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Icon
      Box(
        modifier = Modifier
          .size(80.dp)
          .border(1.dp, NeonCyan.copy(alpha = 0.4f), CircleShape)
          .padding(8.dp),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = "Shield Logo",
          tint = NeonCyan,
          modifier = Modifier.size(40.dp)
        )
      }

      // Titles
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "PROTOCOLO DE PRIVACIDAD",
          color = Color.White,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          letterSpacing = 1.sp,
          textAlign = TextAlign.Center
        )
        Text(
          text = "SENTINEL SECURITY SYSTEM",
          color = NeonCyan,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 10.sp,
          letterSpacing = 2.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      // Legal Terms Scrollable Card
      Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text(
            text = "1. ÁMBITO DE APLICACIÓN LOCAL E INTERCONEXIÓN",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Este sistema opera bajo entornos de red híbridos distribuidos (públicos/privados) mediante asignación dinámica de IPs. Autoriza la recolección, transmisión e impresión técnica de datos operativos en nodos globales certificados.",
            color = LightSlate,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.SansSerif
          )

          Text(
            text = "2. CAPTURA MULTIMODAL LOCALIZADA",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "El operador se compromete a capturar exclusivamente registros fotográficos fidedignos de choferes, matrículas y credenciales vigentes para los procesos de validación perimetral centralizada.",
            color = LightSlate,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.SansSerif
          )

          Text(
            text = "3. SISTEMA EXTENSIBLE DE ALERTAS PASIVAS",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Al inicializar la aplicación, aprueba el canal nativo de notificaciones push de barra de tareas y alertas sonoras críticas internas para salvaguardar la sincronía táctica en tiempo real.",
            color = LightSlate,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.SansSerif
          )
        }
      }

      // Accept button
      Button(
        onClick = { state.currentScreen = Screen.LOGIN },
        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BGDeep),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("terms_accept_button")
      ) {
        Text(
          text = "ACEPTAR TÉRMINOS E INSTALAR CANAL",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp
        )
      }
    }
  }
}

// SCREEN 2: OPERATOR LOGIN
@Composable
fun OperatorLoginScreen(state: TacticalState) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Glow Circular Lock Badge
      Box(
        modifier = Modifier
          .size(90.dp)
          .border(2.dp, NeonCyan.copy(alpha = 0.5f), CircleShape)
          .background(CardBg, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.LockOpen,
          contentDescription = "Lock Logo",
          tint = NeonCyan,
          modifier = Modifier.size(44.dp)
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "ACCESO DE OPERADOR",
          color = Color.White,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          letterSpacing = 1.sp
        )
        Text(
          text = "AUTENTICACIÓN DE CRIPTO-LLAVE LOCAL",
          color = MutedText,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Normal,
          fontSize = 8.sp,
          letterSpacing = 1.5.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 4 Role Access Selector Grid
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RoleGridButton(
            role = Role.OFICIAL,
            title = "OFICIAL",
            icon = Icons.Default.Person,
            borderColor = NeonCyan.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f).testTag("role_oficial_button")
          ) {
            state.selectedRolePending = Role.OFICIAL
            state.pinInput = ""
            state.isAuthModalVisible = true
          }

          RoleGridButton(
            role = Role.SUPERVISOR,
            title = "SUPERVISOR",
            icon = Icons.Default.Visibility,
            borderColor = NeonAmber.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f).testTag("role_supervisor_button")
          ) {
            state.selectedRolePending = Role.SUPERVISOR
            state.pinInput = ""
            state.isAuthModalVisible = true
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RoleGridButton(
            role = Role.COORDINADOR,
            title = "COORDINADOR",
            icon = Icons.Default.Shield,
            borderColor = PurplePill.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f).testTag("role_coordinador_button")
          ) {
            state.selectedRolePending = Role.COORDINADOR
            state.pinInput = ""
            state.isAuthModalVisible = true
          }

          RoleGridButton(
            role = Role.SEP,
            title = "SEP CENTRAL",
            icon = Icons.Default.Public,
            borderColor = NeonGreen.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f).testTag("role_sep_button")
          ) {
            state.selectedRolePending = Role.SEP
            state.pinInput = ""
            state.isAuthModalVisible = true
          }
        }
      }
    }

    // PASSWORD MODAL INLINE
    if (state.isAuthModalVisible) {
      PasswordDialog(state)
    }

    // SEDES MODAL DIALOG (Exclusivo SEP Central)
    if (state.isSedeModalVisible) {
      SedesDialog(state)
    }
  }
}

// Helper Role Selection Card
@Composable
fun RoleGridButton(
  role: Role,
  title: String,
  icon: ImageVector,
  borderColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(1.dp, borderColor),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .clickable(onClick = onClick)
      .height(100.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = when(role) {
          Role.OFICIAL -> NeonCyan
          Role.SUPERVISOR -> NeonAmber
          Role.COORDINADOR -> PurplePill
          Role.SEP -> NeonGreen
        },
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp
      )
    }
  }
}

// Password verification dialog
@Composable
fun PasswordDialog(state: TacticalState) {
  Dialog(
    onDismissRequest = { state.isAuthModalVisible = false },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.85f))
        .padding(32.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .maxHeight(350.dp)
          .padding(horizontal = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "LLAVE PARA ${state.selectedRolePending?.name ?: ""}",
              color = NeonCyan,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 1.sp
            )
            Text(
              text = "INTRODUZCA CREDENCIAL AUTORIZADA",
              color = MutedText,
              fontSize = 8.sp,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 0.5.sp,
              modifier = Modifier.padding(top = 4.dp)
            )
          }

          // Digit Key Input Field
          TextField(
            value = state.pinInput,
            onValueChange = { state.pinInput = it },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            placeholder = {
              Text(
                "••••",
                color = MutedText.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
              )
            },
            colors = TextFieldDefaults.colors(
              focusedContainerColor = Color.Black,
              unfocusedContainerColor = Color.Black,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedIndicatorColor = NeonCyan,
              unfocusedIndicatorColor = NeonCyan.copy(alpha = 0.4f)
            ),
            textStyle = TextStyle(
              fontSize = 20.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              letterSpacing = 8.sp
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("password_input")
          )

          // Actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Button(
              onClick = { state.isAuthModalVisible = false },
              colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = LightSlate),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("password_abort_button")
            ) {
              Text("ABORTAR", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = {
                // Pin Validation Rules
                val verified = when (state.selectedRolePending) {
                  Role.OFICIAL -> {
                    val num = state.pinInput.toIntOrNull()
                    num != null && num in 1..16
                  }
                  Role.SUPERVISOR -> state.pinInput == "003"
                  Role.COORDINADOR -> state.pinInput == "002"
                  Role.SEP -> state.pinInput == "777"
                  else -> false
                }

                if (verified) {
                  state.isAuthModalVisible = false
                  state.activeRole = state.selectedRolePending
                  if (state.activeRole == Role.SEP) {
                    state.isSedeModalVisible = true
                  } else {
                    state.currentScreen = Screen.MAIN
                    state.addConsoleLog("SESIÓN INICIADA COMO OPERADOR: ${state.activeRole?.name}")
                    state.triggerNotification("CONEXIÓN ESTABLECIDA", "Operador validado y enlazado.", NotificationType.SUCCESS)
                  }
                } else {
                  state.triggerNotification("ERROR DE ACCESO", "Llave criptográfica incorrecta.", NotificationType.DANGER)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BGDeep),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("password_verify_button")
            ) {
              Text("VERIFICAR", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

// Sede Selector dialog for SEP Central
@Composable
fun SedesDialog(state: TacticalState) {
  val sedes = listOf(
    "Sede Aragua",
    "Centro de Distribución",
    "Planta Turmero",
    "Centro Logístico Turmero",
    "CEB Maracay",
    "Centro Logístico",
    "Centro de Distribución Barcelona"
  )

  Dialog(
    onDismissRequest = { state.isSedeModalVisible = false },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.9f))
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .maxHeight(450.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Public,
            contentDescription = "Geolocalizado",
            tint = NeonGreen,
            modifier = Modifier.size(32.dp)
          )
          
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "ENLACE GEOLOCALIZADO MULTI-SEDE",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center
            )
            Text(
              text = "SELECCIONE EL NODO TERRITORIAL A CENTRALIZAR",
              color = MutedText,
              fontSize = 8.sp,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 0.5.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(top = 4.dp)
            )
          }

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
              .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            itemsIndexed(sedes) { _, SedeName ->
              TextButton(
                onClick = {
                  state.activeSede = SedeName
                  state.isSedeModalVisible = false
                  state.currentScreen = Screen.MAIN
                  state.addConsoleLog("SESIÓN INICIADA COMO SEP CENTRAL EN $SedeName")
                  state.triggerNotification("ENLACE DE SEDE ESTABLECIDO", "Centralizado en $SedeName.", NotificationType.SUCCESS)
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color.Black, RoundedCornerShape(6.dp))
                  .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                  .testTag("sede_button_${SedeName.replace(" ", "_")}")
              ) {
                Text(
                  text = SedeName,
                  color = LightSlate,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.fillMaxWidth(),
                  textAlign = TextAlign.Left
                )
              }
            }
          }
        }
      }
    }
  }
}

// SCREEN 3: MAIN APP INTERFACE WITH LOWER TABS AND MODALS
@Composable
fun MainDashboardScreen(state: TacticalState) {
  Scaffold(
    topBar = {
      HeaderWidget(state)
    },
    bottomBar = {
      BottomNavWidget(state)
    },
    containerColor = Color.Transparent,
    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        when (state.currentTab) {
          Tab.DASHBOARD -> DashboardContent(state)
          Tab.INTEL -> IntelContent(state)
          Tab.SENSORS -> SensorsContent(state)
          Tab.LOGS -> LogsContent(state)
        }
      }

      // Action menu modal overlay
      if (state.isQuickActionsOpen) {
        QuickActionsDialog(state)
      }
    }
  }
}

// HEADER COMPONENT (M3 design)
@Composable
fun HeaderWidget(state: TacticalState) {
  var isBlinking by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    while(true) {
      delay(800)
      isBlinking = !isBlinking
    }
  }

  Surface(
    color = Color.Black.copy(alpha = 0.95f),
    border = BorderStroke(1.dp, GlassBorder),
    modifier = Modifier.statusBarsPadding()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left Info Info
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.DeveloperMode,
            contentDescription = "Terminal Icon",
            tint = NeonCyan,
            modifier = Modifier.size(16.dp)
          )
        }

        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = if (state.activeRole == Role.OFICIAL) "OFICIAL-BASE" else "M. MATRIX",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
            if (state.activeSede.isNotEmpty()) {
              Box(
                modifier = Modifier
                  .background(Color.Black, RoundedCornerShape(3.dp))
                  .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text(
                  text = state.activeSede.uppercase(),
                  color = NeonGreen,
                  fontSize = 7.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = state.activeRole?.name ?: "UNKNOWN",
              color = NeonCyan,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isBlinking) NeonGreen else NeonGreen.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "LINK SYNC",
              color = NeonGreen,
              fontSize = 7.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }

      // Actions right
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Stealth
        IconButton(
          onClick = {
            state.triggerNotification("MODO STEALTH ACTIVO", "Sensores perimetrales en pasivo.", NotificationType.INFO)
          },
          modifier = Modifier
            .size(30.dp)
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, DarkGray, RoundedCornerShape(6.dp))
            .testTag("stealth_alert_button")
        ) {
          Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = "Stealth Icon",
            tint = LightSlate,
            modifier = Modifier.size(14.dp)
          )
        }

        // Lock
        IconButton(
          onClick = {
            state.triggerNotification("BLOQUEO TÁCTICO", "Puertas automatizadas bloqueadas de emergencia.", NotificationType.DANGER)
          },
          modifier = Modifier
            .size(30.dp)
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .testTag("lock_alert_button")
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Icon",
            tint = NeonRed,
            modifier = Modifier.size(14.dp)
          )
        }

        // Log out
        IconButton(
          onClick = {
            state.activeRole = null
            state.activeSede = ""
            state.currentScreen = Screen.LOGIN
          },
          modifier = Modifier
            .size(30.dp)
            .background(Color.Black, RoundedCornerShape(6.dp))
            .border(1.dp, DarkGray, RoundedCornerShape(6.dp))
            .testTag("logout_button")
        ) {
          Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = "Log Out Icon",
            tint = NeonRed,
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }
  }
}

// BOTTOM NAVIGATION WIDGET
@Composable
fun BottomNavWidget(state: TacticalState) {
  Surface(
    color = CardBg,
    border = BorderStroke(1.dp, GlassBorder)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Tab Dashboard & Tab Intel
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        BottomNavItem(
          icon = Icons.Default.Crosshairs,
          label = "Control",
          isSelected = state.currentTab == Tab.DASHBOARD,
          modifier = Modifier.testTag("tab_dashboard")
        ) {
          state.currentTab = Tab.DASHBOARD
        }

        BottomNavItem(
          icon = Icons.Default.LocationOn,
          label = "Intel",
          isSelected = state.currentTab == Tab.INTEL,
          modifier = Modifier.testTag("tab_intel")
        ) {
          state.currentTab = Tab.INTEL
        }
      }

      // Middle action floating button (QR Menu)
      Box(
        modifier = Modifier
          .size(48.dp)
          .offset(y = (-14).dp)
          .border(4.dp, BGDeep, CircleShape)
          .background(NeonCyan, CircleShape)
          .clickable { state.isQuickActionsOpen = true }
          .testTag("quick_actions_fab"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.QrCode,
          contentDescription = "Quick Actions QR",
          tint = BGDeep,
          modifier = Modifier.size(20.dp)
        )
      }

      // Tab Sensors & Tab Logs
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        BottomNavItem(
          icon = Icons.Default.Satellite,
          label = "Nodos",
          isSelected = state.currentTab == Tab.SENSORS,
          modifier = Modifier.testTag("tab_sensors")
        ) {
          state.currentTab = Tab.SENSORS
        }

        BottomNavItem(
          icon = Icons.Default.List,
          label = "Bitácora",
          isSelected = state.currentTab == Tab.LOGS,
          modifier = Modifier.testTag("tab_logs")
        ) {
          state.currentTab = Tab.LOGS
        }
      }
    }
  }
}

@Composable
fun BottomNavItem(
  icon: ImageVector,
  label: String,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Column(
    modifier = modifier
      .clickable(onClick = onClick)
      .padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (isSelected) NeonCyan else MutedText,
      modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label.uppercase(),
      color = if (isSelected) NeonCyan else MutedText,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 7.5.sp,
      letterSpacing = 0.5.sp
    )
  }
}

// TOAST OVERLAY DESIGN
@Composable
fun NotificationBanner(notif: SystemNotification) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(
      width = 1.dp,
      color = when (notif.type) {
        NotificationType.SUCCESS -> NeonGreen
        NotificationType.DANGER -> NeonRed
        NotificationType.INFO -> NeonCyan
      }
    ),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth(0.92f)
      .shadow(16.dp, RoundedCornerShape(12.dp))
      .testTag("notification_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .background(
            color = when (notif.type) {
              NotificationType.SUCCESS -> GreenPillBg
              NotificationType.DANGER -> RedPillBg
              NotificationType.INFO -> Color.Black
            },
            shape = RoundedCornerShape(8.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = when (notif.type) {
            NotificationType.SUCCESS -> Icons.Default.CheckCircle
            NotificationType.DANGER -> Icons.Default.Warning
            NotificationType.INFO -> Icons.Default.Info
          },
          contentDescription = null,
          tint = when (notif.type) {
            NotificationType.SUCCESS -> NeonGreen
            NotificationType.DANGER -> NeonRed
            NotificationType.INFO -> NeonCyan
          },
          modifier = Modifier.size(16.dp)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = notif.title,
          color = when (notif.type) {
            NotificationType.SUCCESS -> NeonGreen
            NotificationType.DANGER -> NeonRed
            NotificationType.INFO -> NeonCyan
          },
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Black,
          fontSize = 9.sp,
          letterSpacing = 0.5.sp
        )
        Text(
          text = notif.message,
          color = LightSlate,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          lineHeight = 14.sp
        )
      }
    }
  }
}

// QUICK ACTIONS DIALOG MENU
@Composable
fun QuickActionsDialog(state: TacticalState) {
  val commands = listOf(
    "ALERTA TRANSMITIDA" to "Broadcast de hora enviado a todos los oficiales.",
    "REGISTRO RECORRIDO" to "Punto de patrullaje perimetral marcado.",
    "ARMA DISUASIVA ACTIVADA" to "Alerta de despliegue disuasivo en cola.",
    "UBICACIÓN EMITIDA" to "Coordenadas de resguardo actualizadas.",
    "INTRUSO DETECTADO" to "Activando protocolo de contención de perímetro.",
    "REGISTRO FOTOGRÁFICO" to "Almacenando ráfaga perimetral en servidor central.",
    "RELEVO INICIADO" to "Marcaje temporal de alimentación registrado."
  )

  Dialog(
    onDismissRequest = { state.isQuickActionsOpen = false },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.85f))
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().maxHeight(500.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "COMANDOS DE ACCIÓN RÁPIDA",
              color = NeonCyan,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
            IconButton(
              onClick = { state.isQuickActionsOpen = false },
              modifier = Modifier.testTag("quick_action_close_button")
            ) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MutedText)
            }
          }

          Divider(color = GlassBorder)

          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            itemsIndexed(commands) { idx, cmd ->
              val (title, msg) = cmd
              val type = when {
                title.contains("ALERTA") || title.contains("RELEVO") -> NotificationType.INFO
                title.contains("ARMA") || title.contains("INTRUSO") -> NotificationType.DANGER
                else -> NotificationType.SUCCESS
              }

              Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, DarkGray),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    state.isQuickActionsOpen = false
                    state.triggerNotification(title, msg, type)
                  }
                  .testTag("quick_action_item_$idx")
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(
                    imageVector = when(type) {
                      NotificationType.SUCCESS -> Icons.Default.CheckCircle
                      NotificationType.DANGER -> Icons.Default.Warning
                      else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when(type) {
                      NotificationType.SUCCESS -> NeonGreen
                      NotificationType.DANGER -> NeonRed
                      else -> NeonCyan
                    },
                    modifier = Modifier.size(16.dp)
                  )
                  Column {
                    Text(text = title, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(text = msg, color = MutedText, fontSize = 10.sp)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

// TAB 1: DASHBOARD CONTROL (Segregated processes per Role)
@Composable
fun DashboardContent(state: TacticalState) {
  if (state.activeRole == Role.OFICIAL) {
    OficialFormContent(state)
  } else {
    HierarchyDashboardContent(state)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OficialFormContent(state: TacticalState) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = NeonCyan)
          Text(
            text = "RECOLECCIÓN DE DATOS DE INGRESO",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
        Box(
          modifier = Modifier
            .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(text = "PASO 1", color = NeonRed, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
      }

      // Segregated view switcher tab buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.Black, RoundedCornerShape(8.dp))
          .border(1.dp, DarkGray, RoundedCornerShape(8.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val isHumano = state.oficialSubMode == IngressMode.HUMANOS
        Button(
          onClick = { state.oficialSubMode = IngressMode.HUMANOS },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isHumano) NeonCyan else Color.Transparent,
            contentColor = if (isHumano) BGDeep else MutedText
          ),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.weight(1f).testTag("segment_humanos_button")
        ) {
          Text("HUMANOS (PERSONAL)", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Button(
          onClick = { state.oficialSubMode = IngressMode.VEHICULOS },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (!isHumano) NeonCyan else Color.Transparent,
            contentColor = if (!isHumano) BGDeep else MutedText
          ),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.weight(1f).testTag("segment_vehiculos_button")
        ) {
          Text("CARGA / VEHÍCULOS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
      }

      // Dynamic form content fields
      if (state.oficialSubMode == IngressMode.HUMANOS) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomTextField(
              value = state.formName,
              onValueChange = { state.formName = it },
              label = "Nombre",
              placeholder = "Ej. Pedro",
              modifier = Modifier.weight(1f)
            )
            CustomTextField(
              value = state.formLastName,
              onValueChange = { state.formLastName = it },
              label = "Apellido",
              placeholder = "Ej. Perez",
              modifier = Modifier.weight(1f)
            )
          }

          CustomTextField(
            value = state.formCompany,
            onValueChange = { state.formCompany = it },
            label = "Empresa de Procedencia",
            placeholder = "Ej. Alimentos Polar C.A."
          )

          CustomTextField(
            value = state.formReason,
            onValueChange = { state.formReason = it },
            label = "Motivo de Visita / Labor",
            placeholder = "Ej. Reparación de caldera central"
          )

          CustomTextField(
            value = state.formAuthorizedBy,
            onValueChange = { state.formAuthorizedBy = it },
            label = "Funcionario que Autoriza",
            placeholder = "Ej. Ing. Carlos Gomez"
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomTextField(
              value = state.formTimeIn,
              onValueChange = { state.formTimeIn = it },
              label = "Hora Ingreso",
              placeholder = "04:20",
              modifier = Modifier.weight(1f)
            )
            CustomTextField(
              value = state.formTimeOutEstimate,
              onValueChange = { state.formTimeOutEstimate = it },
              label = "Hora Salida Estimada",
              placeholder = "18:00",
              modifier = Modifier.weight(1f)
            )
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Tipo Unidad Select Field
          Text("TIPO DE UNIDAD DE CARGA", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Black, RoundedCornerShape(8.dp))
              .border(1.dp, DarkGray, RoundedCornerShape(8.dp))
              .clickable {
                state.formVehicleType = if (state.formVehicleType == "Gandola Chuto Plano") "Camión Tritón Cifrado" else "Gandola Chuto Plano"
              }
              .padding(12.dp)
          ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text(text = state.formVehicleType, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
              Icon(imageVector = Icons.Default.Refresh, contentDescription = "Switch", tint = NeonCyan, modifier = Modifier.size(14.dp))
            }
          }

          CustomTextField(
            value = state.formPlate,
            onValueChange = { state.formPlate = it },
            label = "Matrícula / Placa de Control",
            placeholder = "Ej. AA123BB"
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomTextField(
              value = state.formWeight,
              onValueChange = { state.formWeight = it },
              label = "Masa de Carga (KG)",
              placeholder = "30000",
              modifier = Modifier.weight(1f)
            )

            Column(modifier = Modifier.weight(1f)) {
              Text("DESTINO / ZONA INTERNA", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
              Spacer(modifier = Modifier.height(4.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color.Black, RoundedCornerShape(8.dp))
                  .border(1.dp, DarkGray, RoundedCornerShape(8.dp))
                  .clickable {
                    state.formDestination = if (state.formDestination == "Almacén de Materia Prima") "Patio de Maniobras Norte" else "Almacén de Materia Prima"
                  }
                  .padding(12.dp)
              ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                  Text(text = state.formDestination, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Icon(imageVector = Icons.Default.Refresh, contentDescription = "Switch", tint = NeonCyan, modifier = Modifier.size(12.dp))
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Biometric Digital Evidence Section
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, DarkGray),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Text(
            text = "EVIDENCIA FOTOGRÁFICA DIGITAL",
            color = NeonCyan,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            EvidenceButton(
              title = "Foto Chofer",
              icon = Icons.Default.Person,
              modifier = Modifier.weight(1f).testTag("photo_driver_button")
            ) {
              state.triggerNotification("CAPTURA COMPLETA", "Registro digital del rostro de chofer archivado.", NotificationType.SUCCESS)
            }
            EvidenceButton(
              title = "Vehículo / CI",
              icon = Icons.Default.Shield,
              modifier = Modifier.weight(1f).testTag("photo_vehicle_button")
            ) {
              state.triggerNotification("CAPTURA COMPLETA", "Captura de carrocería / credencial completada.", NotificationType.SUCCESS)
            }
            EvidenceButton(
              title = "Placa Foto",
              icon = Icons.Default.Crosshairs,
              modifier = Modifier.weight(1f).testTag("photo_plate_button")
            ) {
              state.triggerNotification("CAPTURA COMPLETA", "Matrícula indexada con OCR perimetral.", NotificationType.SUCCESS)
            }
          }
        }
      }

      // Submit Transmission Button
      Button(
        onClick = {
          state.triggerNotification("TRANSMISIÓN COMPLETA", "Datos empaquetados y subidos al servidor central.", NotificationType.SUCCESS)
          state.addConsoleLog("MATRIZ DE INGRESO EMITIDA - CHOFER REGISTRADO OK")
          state.formName = ""
          state.formLastName = ""
          state.formCompany = ""
          state.formReason = ""
          state.formAuthorizedBy = ""
          state.formPlate = ""
          state.formWeight = ""
        },
        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BGDeep),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("transmit_button")
      ) {
        Text("TRANSMITIR MATRIZ A CENTRAL", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

// Helper Evidence captures
@Composable
fun EvidenceButton(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = Color.Black),
    border = BorderStroke(1.dp, DarkGray),
    shape = RoundedCornerShape(8.dp),
    modifier = modifier
      .clickable(onClick = onClick)
      .height(56.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(imageVector = icon, contentDescription = title, tint = MutedText, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = title.uppercase(), color = LightSlate, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
  }
}

// Custom technical styled TextField
@Composable
fun CustomTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = label.toUpperCase(),
      color = MutedText,
      fontSize = 8.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = MutedText.copy(alpha = 0.4f), fontSize = 11.sp) },
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Black,
        unfocusedContainerColor = Color.Black,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedIndicatorColor = NeonCyan,
        unfocusedIndicatorColor = DarkGray
      ),
      textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier.fillMaxWidth()
    )
  }
}

// Tab 1: Dashboard for Superior Hierarchy (Supervisor / Coordinador / SEP)
@Composable
fun HierarchyDashboardContent(state: TacticalState) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.3f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = NeonAmber)
          Text(
            text = "CONSOLA LOGÍSTICA DE VALIDACIÓN",
            color = NeonAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
        Box(
          modifier = Modifier
            .background(NeonAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, NeonAmber.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(text = "LIVE MATRIX", color = NeonAmber, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
      }

      // Exclusive Export tools for SEP Central
      if (state.activeRole == Role.SEP) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(10.dp))
            .border(1.dp, DarkGray, RoundedCornerShape(10.dp))
            .padding(6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Button(
            onClick = {
              state.triggerNotification("DESCARGA SECUENCIA", "Reporte consolidado exportado a formato Excel con firmas criptográficas.", NotificationType.SUCCESS)
              state.addConsoleLog("REPORTE EXPORTADO -> EXCEL CONTRATISTAS GENERADO")
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.1f), contentColor = NeonGreen),
            border = BorderStroke(1.dp, NeonGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(38.dp).testTag("export_excel_button")
          ) {
            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("BAJAR EXCEL", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = {
              state.triggerNotification("DESCARGA SECUENCIA", "Documentación PDF generada de forma inmutable.", NotificationType.SUCCESS)
              state.addConsoleLog("EXPEDIENTES EMITIDOS -> PDF INMUTABLE")
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.1f), contentColor = NeonRed),
            border = BorderStroke(1.dp, NeonRed),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(38.dp).testTag("export_pdf_button")
          ) {
            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("DESCARGAR PDF", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
          }
        }
      }

      // Pending Peticiones list
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "PETICIONES EN ESPERA DE DICTAMEN",
          color = NeonCyan,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )

        state.supervisorQueue.forEachIndexed { idx, item ->
          Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, DarkGray),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Top line time
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "ESPERA: ${item.espera}",
                  color = MutedText,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = item.hora,
                  color = NeonAmber,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              // Body info
              Column {
                Text(text = item.contratista, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                  text = "Doc: ${item.cedula} | Motivo: ${item.motivo}",
                  color = MutedText,
                  fontSize = 9.sp,
                  fontFamily = FontFamily.Monospace
                )
                Text(
                  text = item.supervisor.uppercase(),
                  color = NeonCyan,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }

              // Checkbox indicators
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .border(1.dp, DarkGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                  .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                HierarchyCheckPill(label = "Seguridad OK", isActive = item.valSeg, activeColor = NeonGreen)
                HierarchyCheckPill(label = "Visto Coord", isActive = item.visCoord, activeColor = PurplePill)
                HierarchyCheckPill(label = "Aprob SEP", isActive = item.aprSep, activeColor = NeonGreen)
              }

              // Actions
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    val target = state.supervisorQueue[idx]
                    // Update appropriate flow tag based on current logged in role
                    when(state.activeRole) {
                      Role.OFICIAL -> target.valSeg = true
                      Role.COORDINADOR -> target.visCoord = true
                      Role.SEP -> target.aprSep = true
                      Role.SUPERVISOR -> target.valSeg = true
                      else -> {}
                    }
                    // Re-trigger mutable state update on list
                    state.supervisorQueue[idx] = target.copy()
                    state.triggerNotification("APROBADO", "Acceso e ingreso perimetral validado por jerarquía.", NotificationType.SUCCESS)
                    state.addConsoleLog("APROBACIÓN DE INGRESO -> EXPEDIENTE [${item.id}] ACTUALIZADO")
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = BGDeep),
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier.weight(1f).height(30.dp).testTag("pending_validate_button_$idx")
                ) {
                  Text("VALIDAR", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                  onClick = {
                    state.supervisorQueue.removeAt(idx)
                    state.triggerNotification("RECHAZADO", "Ingreso denegado de forma explícita por puesto superior.", NotificationType.DANGER)
                    state.addConsoleLog("INGRESO DENEGADO PERMANENTEMENTE -> EXPEDIENTE [${item.id}] REMOVIDO")
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = NeonRed),
                  border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier.weight(1f).height(30.dp).testTag("pending_reject_button_$idx")
                ) {
                  Text("RECHAZAR", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      // Bottom tactical active radar map
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "UBICACIÓN DEL PUESTO & COORDENADAS DE GUARDIA",
          color = MutedText,
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )

        TacticalRadarWidget(role = state.activeRole?.name ?: "UNKNOWN")
      }
    }
  }
}

@Composable
fun HierarchyCheckPill(label: String, isActive: Boolean, activeColor: Color) {
  Box(
    modifier = Modifier
      .background(if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(4.dp))
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    Text(
      text = label.uppercase(),
      color = if (isActive) activeColor else MutedText.copy(alpha = 0.4f),
      fontSize = 7.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}

// 3D/Radar aesthetic widget
@Composable
fun TacticalRadarWidget(role: String, companions: List<CompanionGPS> = emptyList()) {
  val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
  val scanAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scanAngle"
  )

  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.5f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(1800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "pulseScale"
  )

  Card(
    colors = CardDefaults.cardColors(containerColor = Color.Black),
    border = BorderStroke(1.dp, DarkGray),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .height(140.dp)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Dynamic Scanning Grid Drawing on Canvas
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .align(Alignment.Center)
      ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radiusMax = size.height * 0.9f / 2

        // Concentric circles
        drawCircle(
          color = NeonCyan.copy(alpha = 0.08f),
          radius = radiusMax,
          center = Offset(centerX, centerY),
          style = Stroke(width = 1f)
        )
        drawCircle(
          color = NeonCyan.copy(alpha = 0.12f),
          radius = radiusMax * 0.6f,
          center = Offset(centerX, centerY),
          style = Stroke(width = 1.2f)
        )
        drawCircle(
          color = NeonCyan.copy(alpha = 0.15f),
          radius = radiusMax * 0.3f,
          center = Offset(centerX, centerY),
          style = Stroke(width = 1.5f)
        )

        // Radial crosshairs
        drawLine(
          color = NeonCyan.copy(alpha = 0.1f),
          start = Offset(centerX - radiusMax, centerY),
          end = Offset(centerX + radiusMax, centerY),
          strokeWidth = 1f
        )
        drawLine(
          color = NeonCyan.copy(alpha = 0.1f),
          start = Offset(centerX, centerY - radiusMax),
          end = Offset(centerX, centerY + radiusMax),
          strokeWidth = 1f
        )

        // Sweeping Radar scanning wedge
        drawArc(
          brush = Brush.sweepGradient(
            0f to NeonCyan.copy(alpha = 0.3f),
            0.5f to NeonCyan.copy(alpha = 0.02f),
            1f to Color.Transparent,
            center = Offset(centerX, centerY)
          ),
          startAngle = scanAngle,
          sweepAngle = 70f,
          useCenter = true,
          topLeft = Offset(centerX - radiusMax, centerY - radiusMax),
          size = androidx.compose.ui.geometry.Size(radiusMax * 2, radiusMax * 2)
        )

        // Draw Static Guardian Location (Radar Center)
        drawCircle(
          color = NeonAmber,
          radius = 5f,
          center = Offset(centerX, centerY)
        )
        drawCircle(
          color = NeonAmber.copy(alpha = 1f - (pulseScale - 0.5f) / 0.8f),
          radius = 5f * pulseScale * 3,
          center = Offset(centerX, centerY),
          style = Stroke(width = 2f)
        )

        // Draw dynamic positions if supplied
        companions.forEach { companion ->
          val mapX = (companion.x / 100f) * size.width
          val mapY = (companion.y / 100f) * size.height
          
          drawCircle(
            color = NeonCyan,
            radius = 4.5f,
            center = Offset(mapX, mapY)
          )
          drawCircle(
            color = NeonCyan.copy(alpha = 0.5f),
            radius = 12f * pulseScale,
            center = Offset(mapX, mapY),
            style = Stroke(width = 1.5f)
          )
        }
      }

      // Indicators overlays
      Box(
        modifier = Modifier
          .padding(8.dp)
          .align(Alignment.TopStart)
          .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
          .border(1.dp, DarkGray, RoundedCornerShape(4.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text("REF: BASE CENTRAL NORTE", color = MutedText, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(Color.Black.copy(alpha = 0.7f))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("ROL: $role", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("COORD: 10.2439° N, 67.4811° W", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
      }
    }
  }
}

// TAB 2: INTEL VIEW (Companion GPS Monitor per user specifications)
@Composable
fun IntelContent(state: TacticalState) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(imageVector = Icons.Default.Crosshairs, contentDescription = null, tint = NeonCyan)
          Text(
            text = "MONITOR GPS DE COMPAÑEROS",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }

      // GPS Map visual taking 50% height
      TacticalRadarWidget(role = state.activeRole?.name ?: "UNKNOWN", companions = state.companionList)

      // Descriptive companion status cards
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("ESTADO OPERATIVO DE FUERZA HUMANA", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

        state.companionList.forEach { pt ->
          Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, DarkGray),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = pt.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = pt.status, color = MutedText, fontSize = 9.sp)
              }

              Box(
                modifier = Modifier
                  .background(Color.Black, RoundedCornerShape(4.dp))
                  .border(1.dp, GlassBorder, RoundedCornerShape(4.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(text = pt.id, color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
              }
            }
          }
        }
      }
    }
  }
}

// TAB 3: NODES (Sensors / Telemetry)
@Composable
fun SensorsContent(state: TacticalState) {
  val droneCoordinates = listOf(
    CompanionGPS("DRN-342", "DRN-342 (Calderas)", 65f, 30f, "Infra-Rojo Activo"),
    CompanionGPS("DRN-343", "DRN-343 (Patio)", 25f, 70f, "Inspección Rutinaria")
  )

  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(imageVector = Icons.Default.Satellite, contentDescription = null, tint = NeonCyan)
          Text(
            text = "SISTEMA DE DRONES PERIMETRALES",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }

      // Drone terminal logs
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, DarkGray),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "[ TERMINAL LOGS - TELEMETRÍA DRONE ]",
            color = MutedText,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          
          Spacer(modifier = Modifier.height(4.dp))

          state.droneLogs.forEach { log ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(text = ">", color = MutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
              Text(text = log, color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
          }
          
          Text(
            text = "> ENLACE ESTABLE - CAPTURA EN MATRIZ INFRARROJA",
            color = NeonGreen,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }

      // Live position locator
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("UBICACIÓN DINÁMICA DRONES 342 / 343 / 344", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .border(1.dp, DarkGray, RoundedCornerShape(12.dp))
        ) {
          TacticalRadarWidget(role = "DRONE-CORE", companions = droneCoordinates)
        }
      }
    }
  }
}

// TAB 4: HISTORIC LOGS BITACORA & INTERACTIVE VISOR DE LOGS / SIMULADOR DE FALLAS
@Composable
fun LogsContent(state: TacticalState) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardBg),
    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(imageVector = Icons.Default.List, contentDescription = null, tint = NeonCyan)
          Text(
            text = "BITÁCORA EXTENDIDA DE NOVEDADES",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
        Box(
          modifier = Modifier
            .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(text = "HISTORIAL", color = NeonCyan, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
      }

      // Nocturnal parameters card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "PARÁMETROS CRÍTICOS NOCTURNOS",
            color = NeonAmber,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Clave de Seguridad: Polar 10",
            color = LightSlate,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Ingeniero de Guardia (GTI): Ing. R. Méndez",
            color = NeonCyan,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Set de Guardia Activo: Escuadrón Alfa 777",
            color = PurplePill,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Divider(color = DarkGray, modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = "Consigna para la ronda de vigilancia: Mantener la monitorización de presión en calderas cada 45 minutos sin excepción.",
            color = MutedText,
            fontSize = 9.sp,
            lineHeight = 12.sp
          )
        }
      }

      // NEW FEATURE: INTERACTIVE CONSOLE LOG VIEWER / ERROR SIMULATOR (Satisfying "visor de logs para estás pendiente de cada dato de consola y ver error")
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "VISOR DE ERRORES & CONSOLA LIVE",
              color = NeonRed,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            // Error simulator action button
            TextButton(
              onClick = {
                state.addConsoleLog("ERROR [CODE-403]: FALLA DE ENLACE SATELITAL EN BANDA KU. RE-RUTEO EN PROGRESO.")
                state.triggerNotification("ERR_BANDA_KU", "Falla detectada en enlace perimetral.", NotificationType.DANGER)
              },
              contentPadding = PaddingValues(0.dp),
              modifier = Modifier.height(20.dp)
            ) {
              Text("SIMULAR ERROR", color = NeonRed, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Real console logs feed box
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(100.dp)
              .background(Color.Black)
              .border(1.dp, DarkGray, RoundedCornerShape(6.dp))
              .padding(6.dp)
          ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              itemsIndexed(state.consoleLogs) { _, logLine ->
                val isError = logLine.contains("ERROR") || logLine.contains("ERR")
                Text(
                  text = logLine,
                  color = if (isError) NeonRed else NeonGreen,
                  fontSize = 8.5.sp,
                  fontFamily = FontFamily.Monospace,
                  lineHeight = 11.sp,
                  modifier = Modifier.padding(bottom = 2.dp)
                )
              }
            }
          }
        }
      }

      // Eventualities feed
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("REGISTRO DE EVENTOS AUTOMATIZADO", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

        state.baseNovedades.forEach { nov ->
          Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, DarkGray),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(text = "[${nov.loc.uppercase()}]", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(text = nov.h, color = MutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = nov.des, color = LightSlate, fontSize = 10.sp, lineHeight = 13.sp)
            }
          }
        }
      }

      // Server Matrix Core Status Widget
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, DarkGray),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "SERVER MATRIX CORE",
              color = NeonGreen,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "UPTIME: 99.98% | LATENCY: 12MS | SECURE",
              color = MutedText,
              fontSize = 8.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          Box(
            modifier = Modifier
              .background(GreenPillBg, RoundedCornerShape(4.dp))
              .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(text = "ONLINE", color = NeonGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
          }
        }
      }
    }
  }
}

