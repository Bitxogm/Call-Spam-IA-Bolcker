// src/screens/AnswerHangupSettingsScreen.tsx
import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, ScrollView, Alert, Switch } from 'react-native';
import { answerHangupService } from '../services/AnswerHangupService';

type AnswerHangupSettingsScreenProps = {
  navigation: any;
};

export default function AnswerHangupSettingsScreen({ navigation }: AnswerHangupSettingsScreenProps) {
  const [isEnabled, setIsEnabled] = useState(false);
  const [mode, setMode] = useState<'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'AI_CONVERSATION'>('HANGUP_IMMEDIATELY');
  const [delay, setDelay] = useState(2);
  const [loading, setLoading] = useState(true);

  // Cargar configuración al iniciar
  useEffect(() => {
    loadSettings();
  }, []);

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
              • Requiere ser app de teléfono predeterminada{'\n'}
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
});
