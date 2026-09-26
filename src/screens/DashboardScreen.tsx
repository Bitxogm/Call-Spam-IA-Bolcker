// src/screens/DashboardScreen.tsx
import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert, NativeModules, ScrollView } from 'react-native';
import { databaseService } from '../services/DataBaseService';
import { contactsService } from '../services/ContactService';

// Importar módulo nativo de Android
const { CallInterceptorModule } = NativeModules;

// Definir el tipo de navegación (TypeScript)
type DashboardScreenProps = {
  navigation: any;
};

export default function DashboardScreen({ navigation }: DashboardScreenProps) {
  // Estados de la app
  const [blockedCalls, setBlockedCalls] = useState(0);
  const [spamNumbers, setSpamNumbers] = useState(0);
  const [contactsCount, setContactsCount] = useState(0);
  const [contactsPermission, setContactsPermission] = useState(false);
  const [radicalMode, setRadicalMode] = useState(false);
  const [loading, setLoading] = useState(true);

  // Cargar datos desde la base de datos al iniciar
  useEffect(() => {
    loadDashboardData();
  }, []);

  // Recargar datos cuando volvemos a esta pantalla
  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      console.log('🏠 Dashboard enfocado - recargando datos...');
      loadDashboardData();
    });

    return unsubscribe;
  }, [navigation]);

  // Función para cargar todos los datos del dashboard
  const loadDashboardData = async () => {
    try {
      console.log('📊 Cargando datos del dashboard...');

      // Cargar datos de la base de datos
      const [
        totalBlockedCalls,
        spamNumbersList,
        radicalModeSetting
      ] = await Promise.all([
        databaseService.getSetting('total_blocked_calls'),
        databaseService.getSpamNumbers(),
        databaseService.getSetting('radical_mode')
      ]);

      // Verificar permisos de contactos
      const hasContactsPermission = await contactsService.checkPermissions();

      // Si tenemos permisos, cargar contactos
      let contactsStats = { totalContacts: 0 };
      if (hasContactsPermission) {
        await contactsService.loadContacts();
        contactsStats = contactsService.getContactsStats();
      }

      // Actualizar estados
      setBlockedCalls(parseInt(totalBlockedCalls || '0'));
      setSpamNumbers(spamNumbersList.length);
      setContactsCount(contactsStats.totalContacts);
      setContactsPermission(hasContactsPermission);
      setRadicalMode(radicalModeSetting === 'true');

      console.log(`✅ Dashboard cargado:`, {
        llamadas: totalBlockedCalls,
        spam: spamNumbersList.length,
        contactos: contactsStats.totalContacts,
        permisos: hasContactsPermission,
        radical: radicalModeSetting
      });
    } catch (error) {
      console.log('❌ Error cargando dashboard:', error);
      Alert.alert('Error', 'No se pudieron cargar las estadísticas');
    } finally {
      setLoading(false);
    }
  };

  // Función para solicitar permisos de contactos
  const requestContactsPermission = async () => {
    try {
      const permission = await contactsService.requestPermissions();

      if (permission.granted) {
        Alert.alert(
          "✅ Permisos Concedidos",
          "¡Ahora el modo radical puede acceder a tus contactos!",
          [{ text: "OK", onPress: () => loadDashboardData() }]
        );
      } else {
        Alert.alert(
          "❌ Permisos Denegados",
          permission.canAskAgain
            ? "Puedes intentarlo de nuevo más tarde"
            : "Ve a Configuración > Apps > SpamBlocker > Permisos para habilitarlos"
        );
      }
    } catch (error) {
      console.log('❌ Error solicitando permisos:', error);
      Alert.alert('Error', 'No se pudieron solicitar los permisos');
    }
  };

  // Función para ir a la pantalla de gestión de números
  const navigateToSpamNumbers = () => {
    navigation.navigate('SpamNumbers');
  };

  // Función para simular bloquear llamada
  const simulateBlockCall = async () => {
    try {
      // Generar número fake para simular
      const fakeSpamNumber = `900${Math.floor(Math.random() * 1000000).toString().padStart(6, '0')}`;

      // Si modo radical está activo, verificar si está en contactos
      let blockReason = 'Simulación de bloqueo';
      if (radicalMode && contactsPermission) {
        const { shouldBlock, reason } = await contactsService.shouldBlockInRadicalMode(fakeSpamNumber);
        blockReason = `Modo radical: ${reason}`;

        if (!shouldBlock) {
          Alert.alert(
            "ℹ️ Llamada Permitida",
            `📞 ${fakeSpamNumber}\\n🟢 ${reason}\\n\\n¡El modo radical habría permitido esta llamada!`
          );
          return;
        }
      }

      // Registrar la llamada bloqueada
      await databaseService.logBlockedCall(fakeSpamNumber, blockReason);
      const newCount = await databaseService.incrementBlockedCalls();

      setBlockedCalls(newCount);

      Alert.alert(
        "📱 Llamada Bloqueada",
        `¡Spam detectado y bloqueado!\\n📞 ${fakeSpamNumber}\\n🛡️ ${blockReason}\\n\\n📊 Total bloqueadas: ${newCount}`
      );

      console.log(`📱 Llamada simulada bloqueada: ${fakeSpamNumber}`);
    } catch (error) {
      console.log('❌ Error simulando bloqueo:', error);
      Alert.alert('Error', 'No se pudo simular el bloqueo');
    }
  };

  // Función para probar notificación de spam
  const testSpamNotification = async () => {
    if (!CallInterceptorModule) {
      Alert.alert('Error', 'CallInterceptorModule no disponible');
      return;
    }

    try {
      const result = await CallInterceptorModule.testSpamNotification();
      Alert.alert('✅ Éxito', result + '\n\n¿Apareció la notificación con botones?');
    } catch (error: any) {
      Alert.alert('❌ Error', error.message || 'No se pudo enviar notificación de prueba');
    }
  };




  // Mostrar loading
  if (loading) {
    return (
      <View style={[styles.container, styles.loadingContainer]}>
        <Text style={styles.loadingText}>📊 Cargando estadísticas...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.scrollContent}>

      {/* HEADER CON MODO ACTUAL */}
      <View style={styles.header}>
        <Text style={styles.title}>🚫 SPAM BLOCKER</Text>
        <Text style={styles.subtitle}>Tu Contestador Anti-Spam</Text>

        <View style={[styles.modeIndicator, radicalMode ? styles.radicalMode : styles.normalMode]}>
          <Text style={styles.modeText}>
            {radicalMode ? "🔴 MODO RADICAL" : "🟢 MODO NORMAL"}
          </Text>
        </View>
      </View>

      {/* ESTADÍSTICAS REALES */}
      <View style={styles.statsContainer}>

        {/* Llamadas bloqueadas */}
        <View style={styles.statCard}>
          <Text style={styles.statNumber}>{blockedCalls}</Text>
          <Text style={styles.statLabel}>📱 Llamadas Bloqueadas</Text>
        </View>

        {/* Números en lista negra */}
        <TouchableOpacity style={styles.statCard} onPress={navigateToSpamNumbers}>
          <Text style={styles.statNumber}>{spamNumbers}</Text>
          <Text style={styles.statLabel}>🚫 Números Bloqueados</Text>
          <Text style={styles.statHint}>👆 Toca para gestionar</Text>
        </TouchableOpacity>

        {/* Contactos cargados */}
        <TouchableOpacity style={styles.statCard} onPress={contactsPermission ? undefined : requestContactsPermission}>
          <Text style={styles.statNumber}>
            {contactsPermission ? contactsCount : "❌"}
          </Text>
          <Text style={styles.statLabel}>📞 Contactos Permitidos</Text>
          <Text style={styles.statHint}>
            {contactsPermission ? "👆 Modo radical activo" : "👆 Toca para dar permisos"}
          </Text>
        </TouchableOpacity>
      </View>

      {/* BOTONES DE ACCIÓN */}
      <View style={styles.buttonsContainer}>

        <TouchableOpacity style={styles.button} onPress={navigateToSpamNumbers}>
          <Text style={styles.buttonText}>📋 Gestionar Números</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.button}
          onPress={() => navigation.navigate('Whitelist')}
        >
          <Text style={styles.buttonText}>👥 Contactos Permitidos</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, { backgroundColor: '#ff9900' }]}
          onPress={() => navigation.navigate('AnswerHangupSettings')}
        >
          <Text style={styles.buttonText}>⚙️ Answer+Hangup</Text>
          <Text style={styles.buttonSubtext}>Configurar auto-respuesta y modos</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, { backgroundColor: '#dc3545' }]}
          onPress={() => navigation.navigate('CallHistory')}
        >
          <Text style={styles.buttonText}>📞 Historial de Spam</Text>
        </TouchableOpacity>

        {/* Herramientas de desarrollo: fuera de builds de release */}
        {__DEV__ && (
          <>
            <TouchableOpacity
              style={[styles.button, { backgroundColor: '#007bff' }]}
              onPress={() => navigation.navigate('Logs')}
            >
              <Text style={styles.buttonText}>📋 Logs de Debug</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.buttonTest} onPress={simulateBlockCall}>
              <Text style={styles.buttonText}>🧪 SIMULAR BLOQUEO</Text>
            </TouchableOpacity>

            {CallInterceptorModule && (
              <TouchableOpacity
                style={[styles.button, { backgroundColor: '#ff9900' }]}
                onPress={testSpamNotification}
              >
                <Text style={styles.buttonText}>🔔 TEST NOTIFICACIÓN</Text>
              </TouchableOpacity>
            )}
          </>
        )}

      </View>
    </ScrollView>
  );
}

// Estilos (mismos de antes)
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a1a',
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#1a1a1a',
  },
  loadingText: {
    color: '#cccccc',
    fontSize: 18,
    fontWeight: 'bold',
  },
  header: {
    alignItems: 'center',
    marginTop: 10,
    marginBottom: 15,
  },
  title: {
    fontSize: 26,
    fontWeight: 'bold',
    color: '#ff4444',
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 15,
    color: '#cccccc',
    marginBottom: 8,
  },
  modeIndicator: {
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 16,
    marginTop: 6,
  },
  normalMode: {
    backgroundColor: '#00aa44',
  },
  radicalMode: {
    backgroundColor: '#ff4444',
  },
  modeText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 13,
  },
  statsContainer: {
    justifyContent: 'flex-start',
    marginBottom: 15,
  },
  statCard: {
    backgroundColor: '#2a2a2a',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 10,
    marginBottom: 8,
    alignItems: 'center',
    minHeight: 70,
  },
  statNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#00ff88',
    marginBottom: 3,
  },
  statLabel: {
    fontSize: 14,
    color: '#cccccc',
    marginBottom: 2,
    textAlign: 'center',
  },
  statHint: {
    fontSize: 11,
    color: '#888888',
    fontStyle: 'italic',
    textAlign: 'center',
  },
  buttonsContainer: {
    gap: 10,
    paddingBottom: 10,
  },
  button: {
    backgroundColor: '#ff4444',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonTest: {
    backgroundColor: '#00aa44',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: 'white',
  },
  buttonSubtext: {
    fontSize: 12,
    color: '#ffff99',
    marginTop: 4,
    fontWeight: '600',
  },

});