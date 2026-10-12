// CallHistoryScreen.tsx
import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Alert,
  Linking,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import CallHistoryService from '../services/CallHistoryService';
import backendSyncService from '../services/BackendSyncService';
import { fusionarHistorial, HistoryRow } from '../utils/historial';
import { blacklistService } from '../services/BlacklistService';
import { databaseService } from '../services/DataBaseService';

const CallHistoryScreen: React.FC = () => {
  const [history, setHistory] = useState<HistoryRow[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [totalCalls, setTotalCalls] = useState(0);

  // useFocusEffect en lugar de useEffect + addListener('focus'): dispara una
  // sola vez por foco, incluido el montaje. Con las dos cosas por separado la
  // primera apertura cargaba dos veces, y getCallLog() tarda 5s en timeout
  // mientras el puerto 5000 siga cerrado.
  useFocusEffect(
    useCallback(() => {
      loadHistory();
    }, [])
  );

  const loadHistory = async () => {
    const [localData, serverData, total] = await Promise.all([
      CallHistoryService.getHistory(),
      backendSyncService.getCallLog(),
      CallHistoryService.getTotalSpamCalls(),
    ]);

    setHistory(fusionarHistorial(localData, serverData));
    setTotalCalls(total + serverData.length);
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadHistory();
    setRefreshing(false);
  };

  /**
   * Mismo camino dual que SpamNumbersScreen: SharedPreferences es lo que lee la
   * capa nativa para bloquear, SQLite es lo que pinta la lista de la UI. Las dos
   * o ninguna — si solo entra en una, la pantalla y el bloqueo real discrepan.
   */
  const handleBlock = (number: string) => {
    if (!number || number === 'Desconocido') {
      Alert.alert('⚠️ No se puede bloquear', 'Esta llamada no tiene un número asociado.');
      return;
    }

    Alert.alert(
      '🚫 Bloquear número',
      `¿Añadir ${number} a la lista negra? Las próximas llamadas de este número se bloquearán.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Bloquear',
          style: 'destructive',
          onPress: async () => {
            try {
              if (await databaseService.isSpamNumber(number)) {
                Alert.alert('ℹ️ Ya estaba', `${number} ya está en la lista negra.`);
                return;
              }

              const [prefsOk, dbOk] = await Promise.all([
                blacklistService.addNumber(number),
                databaseService.addSpamNumber(number, 'Bloqueado desde el historial', 'manual'),
              ]);

              if (prefsOk && dbOk) {
                Alert.alert('✅ Bloqueado', `${number} añadido a la lista negra.`);
              } else {
                Alert.alert('❌ Error', 'No se pudo bloquear el número.');
              }
            } catch (error) {
              console.error('❌ Error bloqueando desde el historial:', error);
              Alert.alert('❌ Error', 'No se pudo bloquear el número.');
            }
          },
        },
      ]
    );
  };

  const handleClearHistory = () => {
    Alert.alert(
      '🗑️ Borrar Historial',
      '¿Estás seguro de que quieres borrar todo el historial de llamadas spam?',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: async () => {
            const [localOk, servidorOk] = await Promise.all([
              CallHistoryService.clearHistory(),
              backendSyncService.clearCallLog(),
            ]);

            if (localOk && servidorOk) {
              setHistory([]);
              setTotalCalls(0);
              Alert.alert('✅ Historial borrado');
            } else if (localOk && !servidorOk) {
              // Hay que decirlo: si no, las del servidor reaparecen al refrescar
              // y parece que el borrado no funcionó.
              await loadHistory();
              Alert.alert(
                '⚠️ Borrado parcial',
                'Se borró el historial local, pero el servidor no respondió. Las llamadas de los Modos 2/3 siguen ahí.'
              );
            } else {
              Alert.alert('❌ Error', 'No se pudo borrar el historial.');
            }
          },
        },
      ]
    );
  };

  const getReasonColor = (reason: string) => {
    switch (reason) {
      case 'Manolo (Modo 2/3)':
        return '#007bff';
      case 'Blacklist':
        return '#dc3545';
      case 'Modo Radical':
        return '#ffc107';
      case 'Premium':
        return '#fd7e14';
      case 'Unknown':
        return '#6c757d';
      default:
        return '#007bff';
    }
  };

  const getReasonIcon = (reason: string) => {
    switch (reason) {
      case 'Manolo (Modo 2/3)':
        return '🤖';
      case 'Blacklist':
        return '🚫';
      case 'Modo Radical':
        return '📵';
      case 'Premium':
        return '💰';
      case 'Unknown':
        return '❓';
      default:
        return '🤖';
    }
  };

  const getActionIcon = (action: string) => {
    switch (action) {
      case 'Answer+Hangup':
        return '🔇';
      case 'Notification':
        return '📲';
      case 'Blocked':
        return '🚫';
      default:
        return '📞';
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>📞 Historial de Spam</Text>
        <Text style={styles.subtitle}>
          Total bloqueadas: {totalCalls} {totalCalls === 1 ? 'llamada' : 'llamadas'}
        </Text>
      </View>

      <View style={styles.actions}>
        <TouchableOpacity
          style={styles.clearButton}
          onPress={handleClearHistory}
          disabled={history.length === 0}
        >
          <Text style={styles.buttonText}>🗑️ Borrar Todo</Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.historyContainer}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
      >
        {history.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No hay llamadas spam todavía</Text>
            <Text style={styles.emptySubtext}>
              Las llamadas bloqueadas aparecerán aquí
            </Text>
          </View>
        ) : (
          history.map((record, index) => (
            <View key={index} style={[
              styles.callEntry,
              { borderLeftColor: record.origen === 'servidor' ? '#007bff' : '#dc3545' }
            ]}>
              <View style={styles.callHeader}>
                <View>
                  <Text style={styles.callNumber}>{record.number}</Text>
                  <Text style={styles.origenBadge}>
                    {record.origen === 'servidor' ? '☁️ Servidor' : '📱 Local'}
                  </Text>
                </View>
                <View
                  style={[
                    styles.reasonBadge,
                    { backgroundColor: getReasonColor(record.reason) },
                  ]}
                >
                  <Text style={styles.reasonText}>
                    {getReasonIcon(record.reason)} {record.reason}
                  </Text>
                </View>
              </View>

              <View style={styles.callDetails}>
                <Text style={styles.callTimestamp}>
                  🕐 {record.timestamp}
                </Text>
                <Text style={styles.callAction}>
                  {getActionIcon(record.action)} {record.action}
                  {record.duracion !== undefined && ` · ⏱️ ${record.duracion}s`}
                </Text>
              </View>

              <View style={styles.rowActions}>
                <TouchableOpacity
                  style={[styles.tellowsButton, styles.rowAction]}
                  onPress={() => Linking.openURL('https://www.tellows.es/num/' + record.number)}
                >
                  <Text style={styles.tellowsButtonText}>🔍 Consultar</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.blockButton, styles.rowAction]}
                  onPress={() => handleBlock(record.number)}
                >
                  <Text style={styles.blockButtonText}>🚫 Bloquear</Text>
                </TouchableOpacity>
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    backgroundColor: '#dc3545',
    padding: 20,
    paddingTop: 40,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 5,
  },
  subtitle: {
    fontSize: 14,
    color: '#e0e0e0',
  },
  actions: {
    flexDirection: 'row',
    padding: 10,
    gap: 10,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#ddd',
  },
  clearButton: {
    flex: 1,
    backgroundColor: '#dc3545',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 14,
  },
  historyContainer: {
    flex: 1,
    padding: 10,
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: 40,
  },
  emptyText: {
    fontSize: 18,
    color: '#666',
    marginBottom: 10,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999',
  },
  callEntry: {
    backgroundColor: '#fff',
    padding: 15,
    marginBottom: 10,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#dc3545',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  callHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  callNumber: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    fontFamily: 'monospace',
  },
  origenBadge: {
    fontSize: 11,
    color: '#888',
    marginTop: 2,
  },
  reasonBadge: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 5,
  },
  reasonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  callDetails: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  callTimestamp: {
    fontSize: 12,
    color: '#666',
  },
  callAction: {
    fontSize: 12,
    color: '#007bff',
    fontWeight: '600',
  },
  rowActions: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  rowAction: {
    flex: 1,
    marginTop: 0,
  },
  blockButton: {
    backgroundColor: '#fff0f0',
    padding: 8,
    borderRadius: 6,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#f6ccd6',
  },
  blockButtonText: {
    fontSize: 12,
    color: '#c73a5b',
    fontWeight: '600',
  },
  tellowsButton: {
    marginTop: 10,
    backgroundColor: '#f0f4ff',
    padding: 8,
    borderRadius: 6,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#ccd6f6',
  },
  tellowsButtonText: {
    fontSize: 12,
    color: '#3a5bc7',
    fontWeight: '600',
  },
});

export default CallHistoryScreen;
