// CallHistoryScreen.tsx
import React, { useState, useEffect } from 'react';
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
import CallHistoryService, { SpamCallRecord } from '../services/CallHistoryService';

const CallHistoryScreen: React.FC = () => {
  const [history, setHistory] = useState<SpamCallRecord[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [totalCalls, setTotalCalls] = useState(0);

  useEffect(() => {
    loadHistory();
  }, []);

  const loadHistory = async () => {
    const historyData = await CallHistoryService.getHistory();
    const total = await CallHistoryService.getTotalSpamCalls();
    setHistory(historyData);
    setTotalCalls(total);
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadHistory();
    setRefreshing(false);
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
            const success = await CallHistoryService.clearHistory();
            if (success) {
              setHistory([]);
              setTotalCalls(0);
              Alert.alert('✅ Historial borrado');
            }
          },
        },
      ]
    );
  };

  const getReasonColor = (reason: string) => {
    switch (reason) {
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
        <TouchableOpacity style={styles.refreshButton} onPress={handleRefresh}>
          <Text style={styles.buttonText}>🔄 Actualizar</Text>
        </TouchableOpacity>
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
            <View key={index} style={styles.callEntry}>
              <View style={styles.callHeader}>
                <Text style={styles.callNumber}>{record.number}</Text>
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
                </Text>
              </View>

              <TouchableOpacity
                style={styles.tellowsButton}
                onPress={() => Linking.openURL('https://www.tellows.es/num/' + record.number)}
              >
                <Text style={styles.tellowsButtonText}>🔍 Consultar</Text>
              </TouchableOpacity>
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
  refreshButton: {
    flex: 1,
    backgroundColor: '#28a745',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
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
