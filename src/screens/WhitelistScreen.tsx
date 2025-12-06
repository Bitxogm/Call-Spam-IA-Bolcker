// src/screens/WhitelistScreen.tsx
import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  Alert,
  Switch,
  ScrollView,
  ActivityIndicator
} from 'react-native';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';
import ContactsService from '../services/ContactsService';

type WhitelistScreenProps = {
  navigation: any;
};

export default function WhitelistScreen({ navigation }: WhitelistScreenProps) {
  const [modoRadical, setModoRadical] = useState(false);
  const [hasContactsPermission, setHasContactsPermission] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSettings();
    checkContactsPermission();
  }, []);

  /**
   * Carga la configuración actual del Modo Radical
   */
  const loadSettings = async () => {
    try {
      const enabled = await ContactsService.isModoRadicalEnabled();
      setModoRadical(enabled);
      console.log(`🔧 Modo Radical: ${enabled ? 'ACTIVADO' : 'DESACTIVADO'}`);
    } catch (error) {
      console.error('❌ Error cargando settings:', error);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Verifica si tenemos permiso de lectura de contactos
   */
  const checkContactsPermission = async () => {
    try {
      const result = await check(PERMISSIONS.ANDROID.READ_CONTACTS);
      setHasContactsPermission(result === RESULTS.GRANTED);
      console.log(`📇 Permiso READ_CONTACTS: ${result}`);
    } catch (error) {
      console.error('❌ Error verificando permiso:', error);
    }
  };

  /**
   * Solicita permiso de lectura de contactos
   */
  const requestContactsPermission = async () => {
    try {
      const result = await request(PERMISSIONS.ANDROID.READ_CONTACTS);
      setHasContactsPermission(result === RESULTS.GRANTED);

      if (result === RESULTS.GRANTED) {
        Alert.alert('✅ Permiso Concedido', 'Ahora puedes usar la whitelist de contactos');
      } else {
        Alert.alert(
          '❌ Permiso Denegado',
          'Sin este permiso, no podemos verificar tus contactos. La app bloqueará todas las llamadas no deseadas pero no podrá identificar tus contactos.'
        );
      }
    } catch (error) {
      console.error('❌ Error solicitando permiso:', error);
    }
  };

  /**
   * Activa/desactiva el Modo Radical
   */
  const toggleModoRadical = async (value: boolean) => {
    if (!hasContactsPermission) {
      Alert.alert(
        '⚠️ Permiso Requerido',
        'Necesitas conceder permiso de lectura de contactos para usar el Modo Radical.',
        [
          { text: 'Cancelar', style: 'cancel' },
          { text: 'Conceder Permiso', onPress: requestContactsPermission }
        ]
      );
      return;
    }

    setModoRadical(value);
    const success = await ContactsService.setModoRadical(value);

    if (success) {
      Alert.alert(
        value ? '🚫 Modo Radical Activado' : '✅ Modo Radical Desactivado',
        value
          ? 'Solo se permitirán llamadas de tus contactos. Todo lo demás será marcado como spam.'
          : 'Se usará la detección inteligente de spam (blacklist, números premium, etc.)'
      );
    } else {
      setModoRadical(!value); // Revertir en caso de error
      Alert.alert('❌ Error', 'No se pudo cambiar el modo');
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>Cargando configuración...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>👥 Whitelist de Contactos</Text>
          <Text style={styles.subtitle}>
            Gestiona qué llamadas permitir automáticamente
          </Text>
        </View>

        {/* Estado de Permisos */}
        <View style={[styles.card, hasContactsPermission ? styles.cardSuccess : styles.cardWarning]}>
          <Text style={styles.cardTitle}>
            {hasContactsPermission ? '✅ Permisos Concedidos' : '⚠️ Permisos Requeridos'}
          </Text>
          <Text style={styles.cardText}>
            {hasContactsPermission
              ? 'La app puede acceder a tus contactos para verificar llamadas.'
              : 'Necesitas conceder permiso de lectura de contactos para usar esta función.'
            }
          </Text>
          {!hasContactsPermission && (
            <TouchableOpacity
              style={styles.permissionButton}
              onPress={requestContactsPermission}
            >
              <Text style={styles.permissionButtonText}>📇 Conceder Permiso</Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Explicación Whitelist Automática */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>🤖 Whitelist Automática</Text>
          <Text style={styles.cardText}>
            {`• Todos tus contactos son automáticamente permitidos\n• No necesitas añadirlos manualmente\n• La app usa PhoneLookup API de Android\n• Funciona con variaciones de formato (+34, 034, etc.)`}
          </Text>
        </View>

        {/* Modo Radical */}
        <View style={styles.card}>
          <View style={styles.modoRadicalHeader}>
            <View style={styles.modoRadicalInfo}>
              <Text style={styles.cardTitle}>🚫 Modo Radical</Text>
              <Text style={styles.cardText}>
                Solo permitir llamadas de contactos, bloquear todo lo demás
              </Text>
            </View>
            <Switch
              trackColor={{ false: '#767577', true: '#ff4444' }}
              thumbColor={modoRadical ? '#f44336' : '#f4f3f4'}
              ios_backgroundColor="#3e3e3e"
              onValueChange={toggleModoRadical}
              value={modoRadical}
              disabled={!hasContactsPermission}
            />
          </View>

          {modoRadical && (
            <View style={styles.warningBox}>
              <Text style={styles.warningText}>
                ⚠️ ADVERTENCIA: Con Modo Radical activo, TODAS las llamadas que no sean de tus contactos serán bloqueadas, incluso números legítimos (entregas, médicos, bancos, etc.)
              </Text>
            </View>
          )}
        </View>

        {/* Flujo de Verificación */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>📊 Flujo de Verificación</Text>
          <View style={styles.flowContainer}>
            <View style={styles.flowStep}>
              <Text style={styles.flowNumber}>1️⃣</Text>
              <Text style={styles.flowText}>¿Es número de emergencia? → PERMITIR</Text>
            </View>
            <View style={styles.flowStep}>
              <Text style={styles.flowNumber}>2️⃣</Text>
              <Text style={styles.flowText}>¿Está en contactos? → PERMITIR</Text>
            </View>
            {modoRadical ? (
              <View style={styles.flowStep}>
                <Text style={styles.flowNumber}>3️⃣</Text>
                <Text style={styles.flowText}>¿Modo Radical activo? → BLOQUEAR TODO LO DEMÁS</Text>
              </View>
            ) : (
              <>
                <View style={styles.flowStep}>
                  <Text style={styles.flowNumber}>3️⃣</Text>
                  <Text style={styles.flowText}>¿Está en blacklist? → BLOQUEAR</Text>
                </View>
                <View style={styles.flowStep}>
                  <Text style={styles.flowNumber}>4️⃣</Text>
                  <Text style={styles.flowText}>¿Es número premium (900, 902, etc.)? → BLOQUEAR</Text>
                </View>
                <View style={styles.flowStep}>
                  <Text style={styles.flowNumber}>5️⃣</Text>
                  <Text style={styles.flowText}>¿Es desconocido/privado? → BLOQUEAR</Text>
                </View>
                <View style={styles.flowStep}>
                  <Text style={styles.flowNumber}>6️⃣</Text>
                  <Text style={styles.flowText}>Todo lo demás → PERMITIR</Text>
                </View>
              </>
            )}
          </View>
        </View>

        {/* Información Adicional */}
        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>💡 Información</Text>
          <Text style={styles.infoText}>
            {`• La whitelist se actualiza automáticamente cuando añades/eliminas contactos\n• PhoneLookup API es muy rápido (milisegundos)\n• No se almacenan contactos en la app (privacidad)\n• Compatible con números internacionales`}
          </Text>
        </View>

        {/* Botón Volver */}
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.backButtonText}>← Volver</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  loadingText: {
    marginTop: 10,
    fontSize: 16,
    color: '#666',
  },
  content: {
    padding: 15,
  },
  header: {
    marginBottom: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 5,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
  },
  card: {
    backgroundColor: 'white',
    borderRadius: 10,
    padding: 15,
    marginBottom: 15,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  cardSuccess: {
    borderLeftWidth: 4,
    borderLeftColor: '#4CAF50',
  },
  cardWarning: {
    borderLeftWidth: 4,
    borderLeftColor: '#FF9800',
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  cardText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  permissionButton: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    padding: 12,
    marginTop: 10,
    alignItems: 'center',
  },
  permissionButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  modoRadicalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  modoRadicalInfo: {
    flex: 1,
    marginRight: 10,
  },
  warningBox: {
    backgroundColor: '#fff3cd',
    borderLeftWidth: 4,
    borderLeftColor: '#ff9800',
    padding: 10,
    borderRadius: 5,
    marginTop: 10,
  },
  warningText: {
    fontSize: 13,
    color: '#856404',
    lineHeight: 18,
  },
  flowContainer: {
    marginTop: 10,
  },
  flowStep: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  flowNumber: {
    fontSize: 18,
    marginRight: 10,
  },
  flowText: {
    flex: 1,
    fontSize: 14,
    color: '#555',
  },
  infoBox: {
    backgroundColor: '#e3f2fd',
    borderRadius: 10,
    padding: 15,
    marginBottom: 20,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1976d2',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 13,
    color: '#0d47a1',
    lineHeight: 20,
  },
  backButton: {
    backgroundColor: '#6c757d',
    borderRadius: 8,
    padding: 15,
    alignItems: 'center',
    marginBottom: 30,
  },
  backButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
});
