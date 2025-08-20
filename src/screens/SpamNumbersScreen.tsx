// src/screens/SpamNumbersScreen.tsx
import React, { useState, useEffect } from 'react';
import { 
  StyleSheet, 
  Text, 
  View, 
  TouchableOpacity, 
  Alert, 
  FlatList,
  TextInput,
  Modal 
} from 'react-native';

// Importar nuestro servicio de base de datos
import { databaseService, SpamNumber } from '../services/DataBaseService';

// Props de la pantalla
type SpamNumbersScreenProps = {
  navigation: any;
};

export default function SpamNumbersScreen({ navigation }: SpamNumbersScreenProps) {
  // Estados
  const [spamNumbers, setSpamNumbers] = useState<SpamNumber[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [newNumber, setNewNumber] = useState('');
  const [newReason, setNewReason] = useState('');
  const [loading, setLoading] = useState(true);

  // Cargar datos desde la base de datos al iniciar la pantalla
  useEffect(() => {
    loadSpamNumbers();
  }, []);

  // Función para cargar números desde la base de datos
  const loadSpamNumbers = async () => {
    try {
      console.log('📋 Cargando números desde la base de datos...');
      const numbers = await databaseService.getSpamNumbers();
      setSpamNumbers(numbers);
      console.log(`✅ Cargados ${numbers.length} números spam`);
    } catch (error) {
      console.log('❌ Error cargando números:', error);
      Alert.alert('Error', 'No se pudieron cargar los números spam');
    } finally {
      setLoading(false);
    }
  };

  // Función para eliminar número de la base de datos
  const deleteSpamNumber = (id: number, number: string) => {
    Alert.alert(
      "🗑️ Eliminar Número",
      `¿Seguro que quieres eliminar ${number}?`,
      [
        { text: "Cancelar", style: "cancel" },
        { 
          text: "Eliminar", 
          style: "destructive",
          onPress: async () => {
            try {
              const success = await databaseService.deleteSpamNumber(id);
              if (success) {
                // Recargar la lista desde la base de datos
                await loadSpamNumbers();
                Alert.alert("✅ Eliminado", "Número eliminado de la lista negra");
              } else {
                Alert.alert("❌ Error", "No se pudo eliminar el número");
              }
            } catch (error) {
              console.log('❌ Error eliminando:', error);
              Alert.alert("❌ Error", "Error eliminando el número");
            }
          }
        }
      ]
    );
  };

  // Función para añadir nuevo número a la base de datos
  const addSpamNumber = async () => {
    if (newNumber.trim() === '') {
      Alert.alert("⚠️ Error", "Introduce un número válido");
      return;
    }

    try {
      const success = await databaseService.addSpamNumber(
        newNumber.trim(),
        newReason.trim() || 'Sin motivo especificado',
        'manual'
      );

      if (success) {
        // Recargar la lista desde la base de datos
        await loadSpamNumbers();
        setNewNumber(''); // Limpiar formulario
        setNewReason('');
        setModalVisible(false); // Cerrar modal
        Alert.alert("✅ Añadido", "Número añadido a la lista negra");
      } else {
        Alert.alert("❌ Error", "No se pudo añadir el número");
      }
    } catch (error) {
      console.log('❌ Error añadiendo:', error);
      Alert.alert("❌ Error", "Error añadiendo el número");
    }
  };

  // Función para añadir prefijos automáticos
  const addAutomaticPrefixes = async () => {
    const prefixes = [
      { prefix: '800', reason: 'Número gratuito comercial' },
      { prefix: '900', reason: 'Tarificación especial' },
      { prefix: '901', reason: 'Tarificación especial' },
      { prefix: '902', reason: 'Tarificación especial' },
      { prefix: '803', reason: 'Servicios de participación' },
      { prefix: '806', reason: 'Servicios de entretenimiento' },
      { prefix: '807', reason: 'Servicios de entretenimiento' },
      { prefix: '905', reason: 'Servicios de valor añadido' }
    ];

    try {
      let addedCount = 0;
      
      for (const { prefix, reason } of prefixes) {
        const numberPattern = `${prefix}XXXXXX`;
        
        // Verificar si ya existe
        const exists = await databaseService.isSpamNumber(numberPattern);
        
        if (!exists) {
          const success = await databaseService.addSpamNumber(
            numberPattern,
            reason,
            'auto'
          );
          if (success) addedCount++;
        }
      }

      // Recargar la lista
      await loadSpamNumbers();

      if (addedCount > 0) {
        Alert.alert("✅ Prefijos Añadidos", `${addedCount} prefijos comerciales añadidos`);
      } else {
        Alert.alert("ℹ️ Info", "Los prefijos automáticos ya están en la lista");
      }
    } catch (error) {
      console.log('❌ Error añadiendo prefijos:', error);
      Alert.alert("❌ Error", "Error añadiendo prefijos automáticos");
    }
  };

  // Renderizar cada elemento de la lista
  const renderSpamNumber = ({ item }: { item: SpamNumber }) => (
    <View style={styles.spamCard}>
      <View style={styles.spamInfo}>
        <Text style={styles.spamNumber}>📞 {item.number}</Text>
        <Text style={styles.spamReason}>{item.reason}</Text>
        <Text style={styles.spamSource}>
          {item.source === 'manual' && '👤 Manual'}
          {item.source === 'auto' && '🤖 Automático'}
          {item.source === 'reported' && '🚨 Reportado'}
          {' • '} {item.date_added}
        </Text>
      </View>
      
      {/* Botón eliminar (solo si es manual) */}
      {item.source === 'manual' && (
        <TouchableOpacity 
          style={styles.deleteButton}
          onPress={() => deleteSpamNumber(item.id, item.number)}
        >
          <Text style={styles.deleteText}>🗑️</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  // Mostrar loading mientras cargan los datos
  if (loading) {
    return (
      <View style={[styles.container, styles.loadingContainer]}>
        <Text style={styles.loadingText}>📋 Cargando números spam...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      
      {/* HEADER CON ESTADÍSTICAS */}
      <View style={styles.header}>
        <Text style={styles.totalCount}>
          📋 {spamNumbers.length} números bloqueados
        </Text>
      </View>

      {/* BOTONES DE ACCIÓN */}
      <View style={styles.actionsContainer}>
        <TouchableOpacity style={styles.addButton} onPress={() => setModalVisible(true)}>
          <Text style={styles.buttonText}>➕ Añadir Número</Text>
        </TouchableOpacity>
        
        <TouchableOpacity style={styles.autoButton} onPress={addAutomaticPrefixes}>
          <Text style={styles.buttonText}>🤖 Prefijos Automáticos</Text>
        </TouchableOpacity>
      </View>

      {/* LISTA DE NÚMEROS SPAM */}
      <FlatList
        data={spamNumbers}
        renderItem={renderSpamNumber}
        keyExtractor={(item) => item.id.toString()}
        style={styles.list}
        showsVerticalScrollIndicator={false}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>📭 No hay números bloqueados</Text>
            <Text style={styles.emptySubText}>Añade números manualmente o usa prefijos automáticos</Text>
          </View>
        }
      />

      {/* MODAL PARA AÑADIR NÚMERO */}
      <Modal
        animationType="slide"
        transparent={true}
        visible={modalVisible}
        onRequestClose={() => setModalVisible(false)}
      >
        <View style={styles.modalContainer}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>➕ Añadir Número Spam</Text>
            
            {/* Input para número */}
            <TextInput
              style={styles.input}
              placeholder="Número de teléfono"
              placeholderTextColor="#666"
              value={newNumber}
              onChangeText={setNewNumber}
              keyboardType="phone-pad"
            />
            
            {/* Input para motivo */}
            <TextInput
              style={styles.input}
              placeholder="Motivo (opcional)"
              placeholderTextColor="#666"
              value={newReason}
              onChangeText={setNewReason}
            />
            
            {/* Botones del modal */}
            <View style={styles.modalButtons}>
              <TouchableOpacity 
                style={styles.cancelButton} 
                onPress={() => setModalVisible(false)}
              >
                <Text style={styles.buttonText}>Cancelar</Text>
              </TouchableOpacity>
              
              <TouchableOpacity style={styles.saveButton} onPress={addSpamNumber}>
                <Text style={styles.buttonText}>Guardar</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

// Estilos (los mismos de antes + nuevos para loading)
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a1a',
    padding: 20,
  },
  loadingContainer: {
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
    marginBottom: 20,
  },
  totalCount: {
    fontSize: 18,
    color: '#cccccc',
    fontWeight: 'bold',
  },
  actionsContainer: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 20,
  },
  addButton: {
    flex: 1,
    backgroundColor: '#00aa44',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  autoButton: {
    flex: 1,
    backgroundColor: '#4444ff',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 16,
  },
  list: {
    flex: 1,
  },
  spamCard: {
    backgroundColor: '#2a2a2a',
    padding: 15,
    borderRadius: 10,
    marginBottom: 10,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  spamInfo: {
    flex: 1,
  },
  spamNumber: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#ff4444',
    marginBottom: 5,
  },
  spamReason: {
    fontSize: 14,
    color: '#cccccc',
    marginBottom: 5,
  },
  spamSource: {
    fontSize: 12,
    color: '#888888',
  },
  deleteButton: {
    padding: 10,
  },
  deleteText: {
    fontSize: 20,
  },
  emptyContainer: {
    alignItems: 'center',
    marginTop: 50,
  },
  emptyText: {
    fontSize: 18,
    color: '#666666',
    marginBottom: 10,
  },
  emptySubText: {
    fontSize: 14,
    color: '#666666',
    textAlign: 'center',
  },
  // Estilos del modal (iguales que antes)
  modalContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  modalContent: {
    backgroundColor: '#2a2a2a',
    padding: 20,
    borderRadius: 15,
    width: '90%',
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#ff4444',
    textAlign: 'center',
    marginBottom: 20,
  },
  input: {
    backgroundColor: '#1a1a1a',
    color: 'white',
    padding: 15,
    borderRadius: 10,
    marginBottom: 15,
    fontSize: 16,
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 10,
  },
  cancelButton: {
    flex: 1,
    backgroundColor: '#666666',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  saveButton: {
    flex: 1,
    backgroundColor: '#00aa44',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
});