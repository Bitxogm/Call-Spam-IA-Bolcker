// src/screens/AnswerHangupSettingsScreen.tsx
import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, ScrollView, Alert, Switch, PermissionsAndroid, Platform, Linking } from 'react-native';
import { answerHangupService } from '../services/AnswerHangupService';

type AnswerHangupSettingsScreenProps = {
  navigation: any;
};

export default function AnswerHangupSettingsScreen({ navigation }: AnswerHangupSettingsScreenProps) {
  const [isEnabled, setIsEnabled] = useState(false);
  const [mode, setMode] = useState<'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'AI_CONVERSATION'>('HANGUP_IMMEDIATELY');
  const [delay, setDelay] = useState(2);
  const [loading, setLoading] = useState(true);
  const [isAccessibilityEnabled, setIsAccessibilityEnabled] = useState(false);
  const [checkingAccessibility, setCheckingAccessibility] = useState(false);

  // Cargar configuración al iniciar
  useEffect(() => {
    loadSettings();
    checkAccessibilityService();
  }, []);

  // Verificar Accessibility Service cuando cambie el modo o se active
  useEffect(() => {
    if (isEnabled && (mode === 'PLAY_MESSAGE' || mode === 'AI_CONVERSATION')) {
      checkAccessibilityService();
    }
  }, [mode, isEnabled]);

  const loadSettings = async () => {
    try {
      const config = await answerHangupService.getConfiguration();

      setIsEnabled(config.enabled);
      setMode(config.mode);
      setDelay(config.hangupDelay);

      console.log('⚙️ Configuración cargada:', config);
    } catch (error) {
      console.log('❌ Error cargando configuración:', error);
      Alert.alert('Error', 'No se pudo cargar la configuración');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleEnabled = async (value: boolean) => {
    // Si se está activando, verificar permisos primero
    if (value) {
      const hasPermissions = await checkAndRequestPermissions();
      if (!hasPermissions) {
        Alert.alert(
          '⚠️ Permisos Faltantes',
          'Se requieren permisos de lectura del registro de llamadas para que Answer+Hangup funcione correctamente.',
          [
            { text: 'Cancelar', style: 'cancel' },
            {
              text: 'Configuración',
              onPress: () => {
                Linking.openSettings();
              }
            }
          ]
        );
        return;
      }
    }

    try {
      await answerHangupService.setEnabled(value);
      setIsEnabled(value);

      Alert.alert(
        value ? '✅ Activado' : '⚠️ Desactivado',
        value
          ? 'Answer+Hangup activado. Las llamadas spam serán procesadas automáticamente.'
          : 'Answer+Hangup desactivado. Solo se usarán otras medidas de bloqueo.'
      );
    } catch (error) {
      Alert.alert('Error', 'No se pudo cambiar el estado');
    }
  };

  /**
   * Muestra diagnóstico de permisos (botón de debug)
   */
  const showPermissionDiagnostic = async () => {
    try {
      const permissions = await answerHangupService.checkCallLogPermission();

      Alert.alert(
        '📋 Diagnóstico de Permisos',
        `READ_PHONE_STATE: ${permissions.hasPhoneState ? '✅ Permitido' : '❌ DENEGADO'}\n` +
        `READ_CALL_LOG: ${permissions.hasCallLog ? '✅ Permitido' : '❌ DENEGADO (CRÍTICO!)'}\n` +
        `ANSWER_PHONE_CALLS: ${permissions.hasAnswerCalls ? '✅ Permitido' : '❌ DENEGADO'}\n\n` +
        `Estado general: ${permissions.allGranted ? '✅ TODO OK' : '⚠️ FALTAN PERMISOS'}`,
        permissions.allGranted
          ? [{ text: 'OK' }]
          : [
              { text: 'Cancelar', style: 'cancel' },
              {
                text: 'Ir a Configuración',
                onPress: () => Linking.openSettings()
              }
            ]
      );
    } catch (error) {
      Alert.alert('Error', 'No se pudo verificar permisos');
    }
  };

  /**
   * Verifica y solicita permisos necesarios para Answer+Hangup
   */
  const checkAndRequestPermissions = async (): Promise<boolean> => {
    try {
      // Verificar permisos actuales
      const permissions = await answerHangupService.checkCallLogPermission();

      console.log('📋 Permisos actuales:', permissions);

      // Si todos los permisos ya están concedidos
      if (permissions.allGranted) {
        return true;
      }

      // Solicitar permisos faltantes
      const permissionsToRequest = [];

      if (!permissions.hasPhoneState) {
        permissionsToRequest.push(PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE);
      }
      if (!permissions.hasCallLog) {
        permissionsToRequest.push(PermissionsAndroid.PERMISSIONS.READ_CALL_LOG);
      }
      if (!permissions.hasAnswerCalls) {
        permissionsToRequest.push(PermissionsAndroid.PERMISSIONS.ANSWER_PHONE_CALLS);
      }

      if (permissionsToRequest.length === 0) {
        return true;
      }

      console.log('📋 Solicitando permisos:', permissionsToRequest);

      // Solicitar todos los permisos a la vez
      const results = await PermissionsAndroid.requestMultiple(permissionsToRequest);

      console.log('📋 Resultados:', results);

      // Verificar si todos fueron concedidos
      const allGranted = Object.values(results).every(
        result => result === PermissionsAndroid.RESULTS.GRANTED
      );

      if (allGranted) {
        Alert.alert(
          '✅ Permisos Concedidos',
          'Todos los permisos necesarios han sido concedidos. Answer+Hangup funcionará correctamente.'
        );
      } else {
        Alert.alert(
          '⚠️ Permisos Faltantes',
          'Algunos permisos no fueron concedidos. Answer+Hangup puede no funcionar correctamente.\n\n' +
          'CRÍTICO: El permiso "Registro de llamadas" es necesario para detectar llamadas entrantes en Android 9+.'
        );
      }

      return allGranted;
    } catch (error) {
      console.error('❌ Error solicitando permisos:', error);
      Alert.alert('Error', 'No se pudieron solicitar los permisos');
      return false;
    }
  };

  /**
   * Verifica si el Accessibility Service está habilitado
   */
  const checkAccessibilityService = async () => {
    try {
      setCheckingAccessibility(true);
      const enabled = await answerHangupService.isAccessibilityServiceEnabled();
      setIsAccessibilityEnabled(enabled);
      console.log('♿ Accessibility Service:', enabled ? 'HABILITADO' : 'DESHABILITADO');
    } catch (error) {
      console.error('❌ Error verificando Accessibility Service:', error);
      setIsAccessibilityEnabled(false);
    } finally {
      setCheckingAccessibility(false);
    }
  };

  /**
   * Abre la configuración de Accesibilidad
   */
  const openAccessibilitySettings = async () => {
    try {
      await answerHangupService.openAccessibilitySettings();

      Alert.alert(
        '📱 Configuración de Accesibilidad',
        'Pasos a seguir:\n\n' +
        '1. Busca "SpamBlocker" o "SpamBlockerApp" en la lista\n' +
        '2. Activa el servicio moviendo el interruptor\n' +
        '3. Acepta el permiso cuando se solicite\n' +
        '4. Regresa a esta pantalla\n\n' +
        'Cuando regreses, la app verificará automáticamente el estado.',
        [
          {
            text: 'OK',
            onPress: () => {
              // Verificar después de 3 segundos para dar tiempo al usuario
              setTimeout(() => {
                checkAccessibilityService();
              }, 3000);
            }
          }
        ]
      );
    } catch (error) {
      Alert.alert('Error', 'No se pudo abrir la configuración de Accesibilidad');
    }
  };

  const handleModeChange = async (newMode: 'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'AI_CONVERSATION') => {
    try {
      await answerHangupService.setMode(newMode);
      setMode(newMode);

      const modeDescriptions = {
        HANGUP_IMMEDIATELY: 'Las llamadas spam se colgarán automáticamente después del delay configurado.',
        PLAY_MESSAGE: 'Se reproducirá un mensaje IVR corporativo para molestar al spammer antes de colgar.',
        AI_CONVERSATION: 'La IA mantendrá una conversación con el spammer (próximamente).'
      };

      Alert.alert(
        '✅ Modo Cambiado',
        modeDescriptions[newMode]
      );
    } catch (error) {
      Alert.alert('Error', 'No se pudo cambiar el modo');
    }
  };

  const handleDelayChange = async (newDelay: number) => {
    try {
      await answerHangupService.setHangupDelay(newDelay);
      setDelay(newDelay);
    } catch (error) {
      Alert.alert('Error', 'No se pudo cambiar el delay');
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.loadingContainer]}>
        <Text style={styles.loadingText}>⚙️ Cargando configuración...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.scrollContent}>

      {/* HEADER */}
      <View style={styles.header}>
        <Text style={styles.title}>⚙️ Answer+Hangup</Text>
        <Text style={styles.subtitle}>Configuración de auto-respuesta</Text>
      </View>

      {/* BOTÓN DE DIAGNÓSTICO */}
      <TouchableOpacity
        style={styles.diagnosticButton}
        onPress={showPermissionDiagnostic}
      >
        <Text style={styles.diagnosticButtonText}>🔍 Verificar Permisos</Text>
      </TouchableOpacity>

      {/* ADVERTENCIA DE ACCESSIBILITY SERVICE */}
      {isEnabled && (mode === 'PLAY_MESSAGE' || mode === 'AI_CONVERSATION') && !isAccessibilityEnabled && (
        <View style={styles.accessibilityWarning}>
          <View style={styles.warningHeader}>
            <Text style={styles.warningIcon}>⚠️</Text>
            <Text style={styles.warningTitle}>Servicio de Accesibilidad Requerido</Text>
          </View>

          <Text style={styles.warningText}>
            Para usar Modo 2 (IVR) o Modo 3 (IA), necesitas activar el Servicio de Accesibilidad de SpamBlocker.
          </Text>

          <Text style={styles.warningDescription}>
            Este servicio permite que la app conteste automáticamente las llamadas spam.
            NO recopila información personal y solo se activa cuando Answer+Hangup está habilitado.
          </Text>

          <TouchableOpacity
            style={styles.accessibilityButton}
            onPress={openAccessibilitySettings}
          >
            <Text style={styles.accessibilityButtonText}>♿ Abrir Configuración de Accesibilidad</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.recheckButton}
            onPress={checkAccessibilityService}
            disabled={checkingAccessibility}
          >
            <Text style={styles.recheckButtonText}>
              {checkingAccessibility ? '🔄 Verificando...' : '🔄 Verificar de nuevo'}
            </Text>
          </TouchableOpacity>
        </View>
      )}

      {/* CONFIRMACIÓN DE ACCESSIBILITY SERVICE */}
      {isEnabled && (mode === 'PLAY_MESSAGE' || mode === 'AI_CONVERSATION') && isAccessibilityEnabled && (
        <View style={styles.accessibilitySuccess}>
          <Text style={styles.successIcon}>✅</Text>
          <Text style={styles.successText}>Servicio de Accesibilidad activado correctamente</Text>
        </View>
      )}

      {/* ENABLE/DISABLE */}
      <View style={styles.section}>
        <View style={styles.switchContainer}>
          <View style={styles.switchLabel}>
            <Text style={styles.sectionTitle}>Activar Answer+Hangup</Text>
            <Text style={styles.sectionDescription}>
              Contestar y procesar automáticamente llamadas spam
            </Text>
          </View>
          <Switch
            value={isEnabled}
            onValueChange={handleToggleEnabled}
            trackColor={{ false: '#767577', true: '#81b0ff' }}
            thumbColor={isEnabled ? '#007bff' : '#f4f3f4'}
          />
        </View>
      </View>

      {/* SELECTOR DE MODO */}
      {isEnabled && (
        <>
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Modo de Acción</Text>
            <Text style={styles.sectionDescription}>
              ¿Qué hacer después de contestar la llamada?
            </Text>

            {/* MODO 1: Colgar inmediatamente */}
            <TouchableOpacity
              style={[
                styles.modeOption,
                mode === 'HANGUP_IMMEDIATELY' && styles.modeOptionSelected
              ]}
              onPress={() => handleModeChange('HANGUP_IMMEDIATELY')}
            >
              <View style={styles.modeHeader}>
                <Text style={styles.modeIcon}>📵</Text>
                <Text style={styles.modeTitle}>Modo 1: Colgar Inmediatamente</Text>
              </View>
              <Text style={styles.modeDescription}>
                Contestar y colgar después de {delay} segundo{delay > 1 ? 's' : ''}.
                Rápido y efectivo.
              </Text>
              {mode === 'HANGUP_IMMEDIATELY' && (
                <Text style={styles.modeStatus}>✅ Activo</Text>
              )}
            </TouchableOpacity>

            {/* MODO 2: Reproducir mensaje IVR */}
            <TouchableOpacity
              style={[
                styles.modeOption,
                mode === 'PLAY_MESSAGE' && styles.modeOptionSelected
              ]}
              onPress={() => handleModeChange('PLAY_MESSAGE')}
            >
              <View style={styles.modeHeader}>
                <Text style={styles.modeIcon}>🔊</Text>
                <Text style={styles.modeTitle}>Modo 2: IVR Corporativo</Text>
              </View>
              <Text style={styles.modeDescription}>
                "Bienvenido... pulse 1 para ventas, pulse 2 para soporte..."
                Mantiene al spammer ocupado 30 segundos.
              </Text>
              {mode === 'PLAY_MESSAGE' && (
                <Text style={styles.modeStatus}>✅ Activo</Text>
              )}
            </TouchableOpacity>

            {/* MODO 3: IA Conversacional (próximamente) */}
            <TouchableOpacity
              style={[
                styles.modeOption,
                styles.modeOptionDisabled,
                mode === 'AI_CONVERSATION' && styles.modeOptionSelected
              ]}
              onPress={() => Alert.alert('🤖 Próximamente', 'La IA conversacional estará disponible en una futura actualización')}
            >
              <View style={styles.modeHeader}>
                <Text style={styles.modeIcon}>🤖</Text>
                <Text style={styles.modeTitle}>Modo 3: IA Conversacional</Text>
                <Text style={styles.comingSoonBadge}>Próximamente</Text>
              </View>
              <Text style={styles.modeDescription}>
                La IA mantendrá una conversación con el spammer usando GPT/Claude.
              </Text>
            </TouchableOpacity>
          </View>

          {/* DELAY (solo para Modo 1) */}
          {mode === 'HANGUP_IMMEDIATELY' && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Delay antes de colgar</Text>
              <Text style={styles.sectionDescription}>
                Segundos de espera antes de colgar (1-5s)
              </Text>

              <View style={styles.delayContainer}>
                {[1, 2, 3, 4, 5].map((seconds) => (
                  <TouchableOpacity
                    key={seconds}
                    style={[
                      styles.delayButton,
                      delay === seconds && styles.delayButtonSelected
                    ]}
                    onPress={() => handleDelayChange(seconds)}
                  >
                    <Text style={[
                      styles.delayButtonText,
                      delay === seconds && styles.delayButtonTextSelected
                    ]}>
                      {seconds}s
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}

          {/* INFO ADICIONAL */}
          <View style={styles.infoBox}>
            <Text style={styles.infoTitle}>ℹ️ Información</Text>
            <Text style={styles.infoText}>
              • Answer+Hangup solo procesa llamadas detectadas como spam{'\n'}
              • Requiere permiso "Registro de llamadas" (READ_CALL_LOG){'\n'}
              • Modo 2 y 3 requieren Servicio de Accesibilidad activado{'\n'}
              • Los logs se guardan en "Logs de Debug"{'\n'}
              • El historial se guarda en "Historial de Spam"
            </Text>
          </View>
        </>
      )}

    </ScrollView>
  );
}

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
  },
  loadingText: {
    color: '#cccccc',
    fontSize: 18,
    fontWeight: 'bold',
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#ff4444',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#cccccc',
  },
  diagnosticButton: {
    backgroundColor: '#007bff',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 20,
  },
  diagnosticButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  section: {
    marginBottom: 25,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 8,
  },
  sectionDescription: {
    fontSize: 14,
    color: '#999999',
    marginBottom: 15,
  },
  switchContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#2a2a2a',
    padding: 16,
    borderRadius: 12,
  },
  switchLabel: {
    flex: 1,
    marginRight: 12,
  },
  modeOption: {
    backgroundColor: '#2a2a2a',
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  modeOptionSelected: {
    borderColor: '#007bff',
    backgroundColor: '#1a3a5a',
  },
  modeOptionDisabled: {
    opacity: 0.6,
  },
  modeHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
    flexWrap: 'wrap',
  },
  modeIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  modeTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ffffff',
    flex: 1,
  },
  comingSoonBadge: {
    backgroundColor: '#ff9900',
    color: '#000000',
    fontSize: 11,
    fontWeight: 'bold',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
    marginLeft: 8,
  },
  modeDescription: {
    fontSize: 14,
    color: '#aaaaaa',
    lineHeight: 20,
  },
  modeStatus: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#00ff88',
    marginTop: 8,
  },
  delayContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: 10,
  },
  delayButton: {
    backgroundColor: '#2a2a2a',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  delayButtonSelected: {
    borderColor: '#007bff',
    backgroundColor: '#1a3a5a',
  },
  delayButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#cccccc',
  },
  delayButtonTextSelected: {
    color: '#ffffff',
  },
  infoBox: {
    backgroundColor: '#2a2a3a',
    padding: 16,
    borderRadius: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#007bff',
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#cccccc',
    lineHeight: 22,
  },
  accessibilityWarning: {
    backgroundColor: '#3a2a1a',
    padding: 16,
    borderRadius: 12,
    marginBottom: 20,
    borderWidth: 2,
    borderColor: '#ff9900',
  },
  warningHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  warningIcon: {
    fontSize: 24,
    marginRight: 8,
  },
  warningTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ff9900',
    flex: 1,
  },
  warningText: {
    fontSize: 14,
    color: '#ffcc88',
    marginBottom: 12,
    lineHeight: 20,
    fontWeight: '600',
  },
  warningDescription: {
    fontSize: 13,
    color: '#cccccc',
    marginBottom: 16,
    lineHeight: 19,
  },
  accessibilityButton: {
    backgroundColor: '#ff9900',
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 10,
  },
  accessibilityButtonText: {
    color: '#000000',
    fontSize: 15,
    fontWeight: 'bold',
  },
  recheckButton: {
    backgroundColor: '#2a2a2a',
    padding: 10,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#555555',
  },
  recheckButtonText: {
    color: '#aaaaaa',
    fontSize: 14,
    fontWeight: '600',
  },
  accessibilitySuccess: {
    backgroundColor: '#1a3a2a',
    padding: 14,
    borderRadius: 12,
    marginBottom: 20,
    borderWidth: 2,
    borderColor: '#00ff88',
    flexDirection: 'row',
    alignItems: 'center',
  },
  successIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  successText: {
    fontSize: 15,
    color: '#00ff88',
    fontWeight: '600',
    flex: 1,
  },
});
