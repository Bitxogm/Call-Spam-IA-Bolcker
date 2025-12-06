// Importaciones de React Native y navegación
import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';

// Importar nuestras pantallas (las vamos a crear)
import DashboardScreen from './src/screens/DashboardScreen';
import SpamNumbersScreen from './src/screens/SpamNumbersScreen';
import AITestScreen from './src/screens/AITestsScreen';
import WhitelistScreen from './src/screens/WhitelistScreen'; 
// Crear el navegador tipo Stack (pantallas apiladas)
const Stack = createStackNavigator();

// Componente principal de la app
export default function App() {
  return (
    // NavigationContainer = contenedor de todas las pantallas
    <NavigationContainer>
      {/* Stack.Navigator = navegador de pantallas apiladas */}
      <Stack.Navigator 
        initialRouteName="Dashboard" // Pantalla inicial
        screenOptions={{
          headerStyle: {
            backgroundColor: '#1a1a1a', // Header oscuro
          },
          headerTintColor: '#ff4444', // Texto rojo
          headerTitleStyle: {
            fontWeight: 'bold',
          },
        }}
      >
        {/* Pantalla 1: Dashboard */}
        <Stack.Screen 
          name="Dashboard" 
          component={DashboardScreen}
          options={{ title: '🚫 Spam Blocker' }}
        />
        
        {/* Pantalla 2: Gestión de números */}
        <Stack.Screen 
          name="SpamNumbers" 
          component={SpamNumbersScreen}
          options={{ title: '📋 Gestionar Números' }}
        />

        {/* Pantalla 3: Testing IA */}
        <Stack.Screen
          name="AITest"
          component={AITestScreen}
          options={{ title: '🤖 Testing IA' }}
        />

        {/* Pantalla 4: Whitelist de Contactos */}
        <Stack.Screen
          name="Whitelist"
          component={WhitelistScreen}
          options={{ title: '👥 Whitelist' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}