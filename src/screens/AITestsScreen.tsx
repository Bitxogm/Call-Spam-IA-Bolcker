// src/screens/AITestScreen.tsx
import React, { useState, useEffect } from 'react';
import { geminiService, SPAM_PERSONALITIES } from '../services/GeminiServices';
import { elevenLabsService } from '../services/ElevenLabService';
import speechRecognitionService from '../services/SpeechRecognitionService';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  TextInput,
  ScrollView,
  Alert,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Animated
} from 'react-native';

type AITestScreenProps = {
  navigation: any;
};

export default function AITestScreen({ navigation }: AITestScreenProps) {
  const [selectedPersonality, setSelectedPersonality] = useState<keyof typeof SPAM_PERSONALITIES>('abuelo');
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Array<{ role: 'user' | 'assistant', content: string, timestamp: Date }>>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isPlayingTTS, setIsPlayingTTS] = useState(false);

  // Estados para Speech Recognition
  const [isRecording, setIsRecording] = useState(false);
  const [isSpeechAvailable, setIsSpeechAvailable] = useState(false);
  const [micScale] = useState(new Animated.Value(1));

  const isGeminiConfigured = geminiService.isConfigured();

  // Verificar disponibilidad de Speech Recognition
  useEffect(() => {
    checkSpeechAvailability();

    return () => {
      // Cleanup al desmontar
      speechRecognitionService.removeAllListeners();
    };
  }, []);

  const checkSpeechAvailability = async () => {
    try {
      const available = await speechRecognitionService.isAvailable();
      setIsSpeechAvailable(available);

      if (!available) {
        console.warn('⚠️ Speech Recognition no disponible en este dispositivo');
      }
    } catch (error) {
      console.error('❌ Error verificando Speech Recognition:', error);
      setIsSpeechAvailable(false);
    }
  };

  const startNewConversation = () => {
    const newConversationId = geminiService.startConversation('TEST_NUMBER', selectedPersonality);
    setConversationId(newConversationId);
    setMessages([]);

    Alert.alert(
      "🤖 Nueva Conversación",
      `Iniciada con personalidad: ${SPAM_PERSONALITIES[selectedPersonality].name}\\n\\n¡Actúa como un spammer y escribe tu primera oferta!`
    );
  };


  const renderMessage = (message: { role: 'user' | 'assistant', content: string, timestamp: Date }, index: number) => (
    <View
      key={index}
      style={[
        styles.messageContainer,
        message.role === 'user' ? styles.userMessage : styles.aiMessage
      ]}
    >
      <Text style={styles.messageRole}>
        {message.role === 'user' ? '📞 TÚ (Spammer)' : `🤖 ${SPAM_PERSONALITIES[selectedPersonality].name}`}
      </Text>
      <Text style={styles.messageContent}>
        {message.content}
      </Text>
      <Text style={styles.messageTime}>
        {message.timestamp.toLocaleTimeString()}
      </Text>
    </View>
  );

  if (!isGeminiConfigured) {
    return (
      <View style={[styles.container, styles.centerContainer]}>
        <Text style={styles.errorTitle}>🔑 API Key Requerida</Text>
        <Text style={styles.errorText}>
          Para usar la IA necesitas configurar tu API key de Gemini en el archivo .env
        </Text>
        <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
          <Text style={styles.buttonText}>← Volver</Text>
        </TouchableOpacity>
      </View>
    );
  }
  // Función para probar voz sin IA
  const testVoice = async (personality: 'manolo' | 'paquita' | 'roberto') => {
    if (isPlayingTTS) return;

    const testTexts = {
      manolo: "¡Ay, hijo! ¿Una oferta? En mis tiempos las ofertas eran diferentes... Déjame que te cuente, que tengo tiempo de sobra. Mi mujer Carmen siempre dice...",
      paquita: "¿Una oferta? ¡Ay, espera que se me quema la comida! ¿Misifú, qué haces ahí? ¿De qué era la oferta? ¡Que no me acuerdo de nada!",
      roberto: "¿Una oferta? Me interesa mucho, pero necesito todos los detalles. ¿Tienen garantía? ¿Y si no me gusta? ¿Puedo devolverlo? Déjeme consultarlo con mi mujer..."
    };

    setIsPlayingTTS(true);

    try {
      const success = await elevenLabsService.speakText(testTexts[personality], personality);

      if (!success) {
        Alert.alert('❌ Error', 'No se pudo reproducir la voz');
      }
    } catch (error) {
      Alert.alert('❌ Error', 'Error reproduciendo audio');
    } finally {
      setIsPlayingTTS(false);
    }
  };

  // Función para enviar mensaje CON voz
  const sendMessageWithVoice = async () => {
    if (!inputMessage.trim() || !conversationId || isLoading) return;

    const userMessage = inputMessage.trim();
    setInputMessage('');
    setIsLoading(true);

    const userMsgObj = {
      role: 'user' as const,
      content: userMessage,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMsgObj]);

    try {
      console.log('🤖 Enviando mensaje a Gemini:', userMessage);
      const response = await geminiService.generateResponse(conversationId, userMessage);

      if (response.success && response.response) {
        const aiMsgObj = {
          role: 'assistant' as const,
          content: response.response,
          timestamp: new Date()
        };

        setMessages(prev => [...prev, aiMsgObj]);
        console.log('✅ Respuesta recibida:', response.response);

        // NUEVO: Reproducir respuesta con TTS
        setIsPlayingTTS(true);
        const personalityMap = {
          abuelo: 'manolo' as const,
          amaDeCasa: 'paquita' as const,
          indeciso: 'roberto' as const
        };

        const voicePersonality = personalityMap[selectedPersonality];
        const ttsSuccess = await elevenLabsService.speakText(response.response, voicePersonality);

        if (!ttsSuccess) {
          Alert.alert('⚠️ Audio', 'La respuesta se generó pero no se pudo reproducir el audio');
        }

        setIsPlayingTTS(false);

        if (response.tokensUsed) {
          console.log(`📊 Tokens usados: ${response.tokensUsed}`);
        }
      } else {
        Alert.alert('❌ Error', response.error || 'No se pudo generar respuesta');
      }
    } catch (error) {
      console.log('❌ Error enviando mensaje:', error);
      Alert.alert('❌ Error', 'Error de conexión con la IA');
    } finally {
      setIsLoading(false);
      setIsPlayingTTS(false);
    }
  };

  // 🎤 Función para PRESIONAR botón de micrófono (Push-to-Talk)
  const handleMicPressIn = async () => {
    if (!conversationId || isLoading || isPlayingTTS || !isSpeechAvailable) {
      return;
    }

    try {
      setIsRecording(true);

      // Animación del botón
      Animated.spring(micScale, {
        toValue: 1.2,
        useNativeDriver: true,
      }).start();

      // Iniciar reconocimiento
      await speechRecognitionService.startListening('es-ES');
      console.log('🎤 Grabación iniciada - HABLA AHORA');

    } catch (error) {
      console.error('❌ Error iniciando grabación:', error);
      setIsRecording(false);

      Animated.spring(micScale, {
        toValue: 1,
        useNativeDriver: true,
      }).start();

      Alert.alert('❌ Error', 'No se pudo iniciar el reconocimiento de voz');
    }
  };

  // 🎤 Función para SOLTAR botón de micrófono (Push-to-Talk)
  const handleMicPressOut = async () => {
    if (!isRecording) return;

    try {
      // Animación del botón
      Animated.spring(micScale, {
        toValue: 1,
        useNativeDriver: true,
      }).start();

      console.log('🛑 Grabación detenida - Procesando...');
      setIsLoading(true);

      // Detener reconocimiento y obtener resultado
      const transcription = await speechRecognitionService.recognize('es-ES');

      setIsRecording(false);

      if (!transcription || transcription.trim() === '') {
        console.log('⚠️ No se detectó voz');
        setIsLoading(false);
        Alert.alert('⚠️ Sin voz', 'No se detectó ninguna voz. Intenta de nuevo.');
        return;
      }

      console.log('📝 Transcripción:', transcription);

      // Enviar mensaje transcrito a la IA (reusar lógica existente)
      await sendTranscribedMessage(transcription);

    } catch (error) {
      console.error('❌ Error procesando voz:', error);
      setIsRecording(false);
      setIsLoading(false);

      Alert.alert('❌ Error', 'Error al procesar el audio. Intenta de nuevo.');
    }
  };

  // Función auxiliar para enviar mensaje transcrito
  const sendTranscribedMessage = async (text: string) => {
    const userMsgObj = {
      role: 'user' as const,
      content: text,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMsgObj]);

    try {
      console.log('🤖 Enviando transcripción a Gemini:', text);
      const response = await geminiService.generateResponse(conversationId!, text);

      if (response.success && response.response) {
        const aiMsgObj = {
          role: 'assistant' as const,
          content: response.response,
          timestamp: new Date()
        };

        setMessages(prev => [...prev, aiMsgObj]);
        console.log('✅ Respuesta recibida:', response.response);

        // Reproducir respuesta con TTS
        setIsPlayingTTS(true);
        const personalityMap = {
          abuelo: 'manolo' as const,
          amaDeCasa: 'paquita' as const,
          indeciso: 'roberto' as const
        };

        const voicePersonality = personalityMap[selectedPersonality];
        const ttsSuccess = await elevenLabsService.speakText(response.response, voicePersonality);

        if (!ttsSuccess) {
          Alert.alert('⚠️ Audio', 'La respuesta se generó pero no se pudo reproducir el audio');
        }

        setIsPlayingTTS(false);

        if (response.tokensUsed) {
          console.log(`📊 Tokens usados: ${response.tokensUsed}`);
        }
      } else {
        Alert.alert('❌ Error', response.error || 'No se pudo generar respuesta');
      }
    } catch (error) {
      console.log('❌ Error enviando mensaje:', error);
      Alert.alert('❌ Error', 'Error de conexión con la IA');
    } finally {
      setIsLoading(false);
    }
  };



  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>🤖 Testing IA Anti-Spam</Text>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.personalitySelector}>
          {Object.entries(SPAM_PERSONALITIES).map(([key, personality]) => (
            <TouchableOpacity
              key={key}
              style={[
                styles.personalityButton,
                selectedPersonality === key && styles.personalityButtonActive
              ]}
              onPress={() => setSelectedPersonality(key as keyof typeof SPAM_PERSONALITIES)}
            >
              <Text style={[
                styles.personalityButtonText,
                selectedPersonality === key && styles.personalityButtonTextActive
              ]}>
                {personality.name}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
        <View style={styles.voiceTestContainer}>
          <Text style={styles.voiceTestTitle}>🗣️ Probar Voces</Text>
          <View style={styles.voiceTestButtons}>
            <TouchableOpacity
              style={[styles.voiceTestButton, isPlayingTTS && styles.voiceTestButtonDisabled]}
              onPress={() => testVoice('manolo')}
              disabled={isPlayingTTS}
            >
              <Text style={styles.voiceTestButtonText}>
                {isPlayingTTS ? '🔊' : '🧓'} Manolo
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.voiceTestButton, isPlayingTTS && styles.voiceTestButtonDisabled]}
              onPress={() => testVoice('paquita')}
              disabled={isPlayingTTS}
            >
              <Text style={styles.voiceTestButtonText}>
                {isPlayingTTS ? '🔊' : '👵'} Paquita
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.voiceTestButton, isPlayingTTS && styles.voiceTestButtonDisabled]}
              onPress={() => testVoice('roberto')}
              disabled={isPlayingTTS}
            >
              <Text style={styles.voiceTestButtonText}>
                {isPlayingTTS ? '🔊' : '🤔'} Roberto
              </Text>
            </TouchableOpacity>
          </View>
        </View>

        <TouchableOpacity
          style={styles.newConversationButton}
          onPress={startNewConversation}
        >
          <Text style={styles.buttonText}>
            {conversationId ? '🔄 Nueva Conversación' : '🚀 Empezar Testing'}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.conversationContainer}>
        {conversationId ? (
          <>
            <ScrollView style={styles.messagesContainer} showsVerticalScrollIndicator={false}>
              {messages.length === 0 ? (
                <View style={styles.emptyContainer}>
                  <Text style={styles.emptyText}>🎭 Conversación iniciada</Text>
                  <Text style={styles.emptySubText}>
                    Actúa como un spammer y escribe tu oferta comercial
                  </Text>
                </View>
              ) : (
                messages.map(renderMessage)
              )}

              {isLoading && (
                <View style={styles.loadingContainer}>
                  <ActivityIndicator size="small" color="#00ff88" />
                  <Text style={styles.loadingText}>🤖 Generando respuesta...</Text>
                </View>
              )}
            </ScrollView>


            <KeyboardAvoidingView
              behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
              keyboardVerticalOffset={100}
            >
              {/* 🎤 BOTÓN PUSH-TO-TALK (Micrófono) */}
              {isSpeechAvailable && (
                <View style={styles.micContainer}>
                  <Animated.View
                    style={[
                      styles.micButtonWrapper,
                      {
                        transform: [{ scale: micScale }]
                      }
                    ]}
                  >
                    <TouchableOpacity
                      style={[
                        styles.micButton,
                        isRecording && styles.micButtonRecording,
                        (isLoading || isPlayingTTS) && styles.micButtonDisabled
                      ]}
                      onPressIn={handleMicPressIn}
                      onPressOut={handleMicPressOut}
                      disabled={isLoading || isPlayingTTS}
                      activeOpacity={0.8}
                    >
                      <Text style={styles.micButtonIcon}>
                        {isRecording ? '🔴' : isLoading ? '⏳' : isPlayingTTS ? '🔊' : '🎤'}
                      </Text>
                    </TouchableOpacity>
                  </Animated.View>

                  <Text style={styles.micInstructions}>
                    {isRecording
                      ? '🗣️ Hablando... (Suelta para enviar)'
                      : isLoading
                      ? '⏳ Procesando...'
                      : isPlayingTTS
                      ? '🔊 IA respondiendo...'
                      : '🎤 Mantén presionado para hablar'}
                  </Text>
                </View>
              )}

              {/* Input de texto (fallback) */}
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.messageInput}
                  placeholder="O escribe: '¡Oferta especial solo hoy!'"
                  placeholderTextColor="#666"
                  value={inputMessage}
                  onChangeText={setInputMessage}
                  multiline
                  maxLength={500}
                  editable={!isLoading && !isPlayingTTS && !isRecording}
                  textAlignVertical="top"
                  numberOfLines={2}
                />

                <TouchableOpacity
                  style={[styles.sendButton, (!inputMessage.trim() || isLoading || isPlayingTTS || isRecording) && styles.sendButtonDisabled]}
                  onPress={sendMessageWithVoice}
                  disabled={!inputMessage.trim() || isLoading || isPlayingTTS || isRecording}
                >
                  <Text style={styles.sendButtonText}>
                    {isLoading ? '⏳' : isPlayingTTS ? '🔊' : '📞'}
                  </Text>
                </TouchableOpacity>
              </View>
            </KeyboardAvoidingView>

          </>
        ) : (
          <View style={styles.noConversationContainer}>
            <Text style={styles.noConversationText}>
              🎯 Selecciona una personalidad y empezar testing
            </Text>
            <Text style={styles.noConversationSubText}>
              Podrás probar cómo responde cada personalidad a ofertas comerciales
            </Text>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a1a',
  },
  centerContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  header: {
    padding: 20,
    paddingBottom: 10,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ff4444',
    textAlign: 'center',
    marginBottom: 15,
  },
  personalitySelector: {
    marginBottom: 15,
  },
  personalityButton: {
    backgroundColor: '#2a2a2a',
    paddingHorizontal: 15,
    paddingVertical: 8,
    borderRadius: 20,
    marginRight: 10,
  },
  personalityButtonActive: {
    backgroundColor: '#ff4444',
  },
  personalityButtonText: {
    color: '#cccccc',
    fontSize: 14,
    fontWeight: 'bold',
  },
  personalityButtonTextActive: {
    color: 'white',
  },
  newConversationButton: {
    backgroundColor: '#00aa44',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 16,
  },

  conversationContainer: {
    flex: 1,
    padding: 20,
    paddingTop: 0,
  },
  messagesContainer: {
    flex: 1,
    marginBottom: 15,
  },
  messageContainer: {
    marginBottom: 15,
    padding: 15,
    borderRadius: 10,
  },
  userMessage: {
    backgroundColor: '#ff4444',
    alignSelf: 'flex-end',
    maxWidth: '80%',
  },
  aiMessage: {
    backgroundColor: '#2a2a2a',
    alignSelf: 'flex-start',
    maxWidth: '80%',
  },
  messageRole: {
    fontSize: 12,
    fontWeight: 'bold',
    marginBottom: 5,
    color: '#cccccc',
  },
  messageContent: {
    fontSize: 16,
    color: 'white',
    lineHeight: 22,
  },
  messageTime: {
    fontSize: 10,
    color: '#888888',
    marginTop: 5,
    textAlign: 'right',
  },
  inputContainer: {
    flexDirection: 'row',
    gap: 10,
  },
  messageInput: {
    flex: 1,
    backgroundColor: '#2a2a2a',
    color: 'white',
    padding: 15,
    borderRadius: 10,
    maxHeight: 60,
    fontSize: 16,
  },
  sendButton: {
    backgroundColor: '#00aa44',
    width: 50,
    height: 50,
    borderRadius: 25,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sendButtonDisabled: {
    backgroundColor: '#666666',
  },
  sendButtonText: {
    fontSize: 20,
  },
  emptyContainer: {
    alignItems: 'center',
    marginTop: 50,
  },
  emptyText: {
    fontSize: 18,
    color: '#cccccc',
    marginBottom: 10,
  },
  emptySubText: {
    fontSize: 14,
    color: '#666666',
    textAlign: 'center',
  },
  noConversationContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  noConversationText: {
    fontSize: 18,
    color: '#cccccc',
    marginBottom: 10,
    textAlign: 'center',
  },
  noConversationSubText: {
    fontSize: 14,
    color: '#666666',
    textAlign: 'center',
  },
  loadingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 15,
  },
  loadingText: {
    color: '#00ff88',
    marginLeft: 10,
    fontSize: 14,
  },
  errorTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ff4444',
    marginBottom: 20,
    textAlign: 'center',
  },
  errorText: {
    fontSize: 16,
    color: '#cccccc',
    marginBottom: 20,
    textAlign: 'center',
  },
  backButton: {
    backgroundColor: '#666666',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },

  voiceTestContainer: {
    marginBottom: 15,
    padding: 15,
    backgroundColor: '#2a2a2a',
    borderRadius: 10,
  },
  voiceTestTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#00ff88',
    textAlign: 'center',
    marginBottom: 10,
  },
  voiceTestButtons: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  voiceTestButton: {
    backgroundColor: '#4444ff',
    paddingHorizontal: 15,
    paddingVertical: 8,
    borderRadius: 15,
    minWidth: 80,
    alignItems: 'center',
  },
  voiceTestButtonDisabled: {
    backgroundColor: '#666666',
  },
  voiceTestButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
  },

  // Estilos para botón de micrófono (Push-to-Talk)
  micContainer: {
    alignItems: 'center',
    marginBottom: 20,
  },
  micButtonWrapper: {
    marginBottom: 10,
  },
  micButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#00aa44',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 8,
  },
  micButtonRecording: {
    backgroundColor: '#ff4444',
  },
  micButtonDisabled: {
    backgroundColor: '#666666',
    opacity: 0.5,
  },
  micButtonIcon: {
    fontSize: 40,
  },
  micInstructions: {
    fontSize: 14,
    color: '#cccccc',
    textAlign: 'center',
    fontStyle: 'italic',
  },
});