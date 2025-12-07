// LogsScreen.tsx
import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Alert,
} from 'react-native';
import LogsService, { LogEntry } from '../services/LogsService';

const LogsScreen: React.FC = () => {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadLogs();
  }, []);

  const loadLogs = async () => {
    const logsData = await LogsService.getLogs();
    setLogs(logsData);
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadLogs();
    setRefreshing(false);
  };

  const handleClearLogs = () => {
    Alert.alert(
      '🗑️ Borrar Logs',
      '¿Estás seguro de que quieres borrar todos los logs?',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: async () => {
            const success = await LogsService.clearLogs();
            if (success) {
              setLogs([]);
              Alert.alert('✅ Logs borrados');
            }
          },
        },
      ]
    );
  };

  const getLevelColor = (level: string) => {
    switch (level) {
      case 'ERROR':
        return '#dc3545';
      case 'WARNING':
        return '#ffc107';
      case 'INFO':
        return '#17a2b8';
      case 'DEBUG':
        return '#6c757d';
      default:
        return '#000';
    }
  };

  const getLevelIcon = (level: string) => {
    switch (level) {
      case 'ERROR':
        return '❌';
      case 'WARNING':
        return '⚠️';
      case 'INFO':
        return 'ℹ️';
      case 'DEBUG':
        return '🔍';
      default:
        return '📝';
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>📋 Logs de Debug</Text>
        <Text style={styles.subtitle}>
          Total: {logs.length} {logs.length === 1 ? 'log' : 'logs'}
        </Text>
      </View>

      <View style={styles.actions}>
        <TouchableOpacity style={styles.refreshButton} onPress={handleRefresh}>
          <Text style={styles.buttonText}>🔄 Actualizar</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.clearButton}
          onPress={handleClearLogs}
          disabled={logs.length === 0}
        >
          <Text style={styles.buttonText}>🗑️ Borrar Todo</Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.logsContainer}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
      >
        {logs.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No hay logs todavía</Text>
            <Text style={styles.emptySubtext}>
              Los logs de debug aparecerán aquí
            </Text>
          </View>
        ) : (
          logs.map((log, index) => (
            <View key={index} style={styles.logEntry}>
              <View style={styles.logHeader}>
                <Text style={styles.logTimestamp}>{log.timestamp}</Text>
                <View
                  style={[
                    styles.logLevelBadge,
                    { backgroundColor: getLevelColor(log.level) },
                  ]}
                >
                  <Text style={styles.logLevelText}>
                    {getLevelIcon(log.level)} {log.level}
                  </Text>
                </View>
              </View>
              <Text style={styles.logMessage}>{log.message}</Text>
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
    backgroundColor: '#007bff',
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
  logsContainer: {
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
  logEntry: {
    backgroundColor: '#fff',
    padding: 12,
    marginBottom: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#007bff',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  logTimestamp: {
    fontSize: 12,
    color: '#666',
    fontFamily: 'monospace',
  },
  logLevelBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  logLevelText: {
    color: '#fff',
    fontSize: 11,
    fontWeight: 'bold',
  },
  logMessage: {
    fontSize: 14,
    color: '#333',
    fontFamily: 'monospace',
  },
});

export default LogsScreen;
