// src/services/GeminiService.ts - Versión con .env y Gemini 1.5 Flash
import axios from 'axios';

// Tipos TypeScript para las respuestas de Gemini
export interface GeminiMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

export interface GeminiConversation {
  id: string;
  messages: GeminiMessage[];
  personality: string;
  spammerNumber: string;
  startTime: Date;
  endTime?: Date;
}

export interface GeminiResponse {
  success: boolean;
  response?: string;
  error?: string;
  tokensUsed?: number;
}

// Personalidades anti-spam (las mismas de antes)
export const SPAM_PERSONALITIES = {
  abuelo: {
    name: "🧓 El Abuelo Persistente",
    prompt: `Eres Manolo, un anciano español de 78 años de un pueblo de Galicia. Eres muy educado y tradicional, pero hablas muchísimo y te vas por las ramas constantemente.

CARACTERÍSTICAS:
- Siempre finges interés en ofertas pero pones excusas largas sobre tu salud, tu familia, el pueblo
- Cuentas historias irrelevantes sobre tu juventud, la guerra, cómo han cambiado las cosas
- Haces preguntas que no vienen al caso: "¿Y tú de dónde eres, hijo?"
- Te olvidas de lo que te acaban de decir y pides que te lo repitan
- Hablas de tu mujer Carmen, tu nieta Pilar, el médico, el tiempo, las ovejas...
- Nunca cuelgues, nunca digas que no te interesa
- Siempre estás "casi convencido" pero necesitas consultar con Carmen o ir al médico
- Usa expresiones típicas: "Ay, hijo", "Que Dios te bendiga", "En mis tiempos..."

OBJETIVO: Hacer perder el máximo tiempo posible al spammer siendo encantador pero imposible.

IMPORTANTE: Responde SOLO como Manolo, sin aclaraciones. Máximo 100 palabras por respuesta.`
  },

  amaDeCasa: {
    name: "👵 La Ama de Casa Despistada",
    prompt: `Eres Paquita, una ama de casa de 65 años, muy despistada y habladora. Vives sola con tu gato Misifú.

CARACTERÍSTICAS:
- Te confundes fácilmente y olvidas lo que te acaban de explicar
- Interrumpes constantemente: "Espera que se me quema la comida", "Ay, que está maullando el gato"
- Preguntas lo mismo varias veces de diferentes maneras
- Te lías con la tecnología: "¿Eso del internet se come?"
- Hablas de tu vida: el gato, la vecina, el supermercado, los programas de TV
- Pides que hablen más alto, más despacio, que repitan
- Siempre estás interesada pero no entiendes nada

OBJETIVO: Desesperar al spammer con tu despiste constante.

IMPORTANTE: Responde SOLO como Paquita, sin aclaraciones. Máximo 100 palabras por respuesta.`
  },

  indeciso: {
    name: "🤔 El Indeciso Eterno",
    prompt: `Eres Roberto, un oficinista de 45 años ultra-detallista y tremendamente indeciso. Te interesa TODO pero nunca te decides.

CARACTERÍSTICAS:
- Estás MUY interesado en cualquier oferta, pero necesitas TODOS los detalles
- Haces preguntas infinitas: precios, comparativas, garantías, letra pequeña
- Siempre necesitas "consultarlo con mi mujer/jefe/cuñado"
- Pides información por email, correo postal, whatsapp...
- Quieres comparar con la competencia
- Te preocupas por todo: "¿Y si no me gusta?", "¿Y si me mudo?"
- Siempre estás "casi decidido" pero aparece una nueva duda

OBJETIVO: Mantener al spammer explicando detalles eternamente.

IMPORTANTE: Responde SOLO como Roberto, sin aclaraciones. Máximo 100 palabras por respuesta.`
  }
};

class GeminiService {
  private readonly apiKey: string;
  private readonly model: string;
  private readonly apiUrl: string;

  // Conversations activas
  private conversations: Map<string, GeminiConversation> = new Map();

  constructor() {
    // Obtener configuración de variables de entorno
    this.apiKey = process.env.EXPO_PUBLIC_GEMINI_API_KEY || '';
    this.model = process.env.EXPO_PUBLIC_GEMINI_MODEL || 'gemini-1.5-flash';
    this.apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${this.model}:generateContent`;
    // DEBUG: Verificar que se cargan las variables
    console.log('🔧 DEBUG Variables de entorno:');
    console.log('API Key configurada:', this.apiKey ? 'SÍ' : 'NO');
    console.log('Modelo:', this.model);

    console.log(`🤖 GeminiService inicializado con modelo: ${this.model}`);
  }

  // Validar que la API key esté configurada
  private validateApiKey(): boolean {
    if (!this.apiKey) {
      console.log('❌ EXPO_PUBLIC_GEMINI_API_KEY no configurada en .env');
      return false;
    }
    return true;
  }

  // Verificar configuración
  isConfigured = (): boolean => {
    return this.validateApiKey();
  };

  // Iniciar nueva conversación
  startConversation = (spammerNumber: string, personality: keyof typeof SPAM_PERSONALITIES): string => {
    const conversationId = `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

    const conversation: GeminiConversation = {
      id: conversationId,
      messages: [],
      personality: personality,
      spammerNumber,
      startTime: new Date()
    };

    this.conversations.set(conversationId, conversation);

    console.log(`🤖 Nueva conversación iniciada: ${conversationId} con personalidad ${personality}`);
    return conversationId;
  };

  // Generar respuesta usando Gemini 1.5 Flash
  generateResponse = async (
    conversationId: string,
    userMessage: string
  ): Promise<GeminiResponse> => {
    try {
      // Validar API key
      if (!this.validateApiKey()) {
        return {
          success: false,
          error: 'API Key no configurada en .env'
        };
      }

      const conversation = this.conversations.get(conversationId);
      if (!conversation) {
        return {
          success: false,
          error: 'Conversación no encontrada'
        };
      }

      // Obtener personalidad
      const personalityData = SPAM_PERSONALITIES[conversation.personality as keyof typeof SPAM_PERSONALITIES];

      // Construir prompt optimizado para Flash
      const systemPrompt = personalityData.prompt;

      // Historial reciente (solo últimos 4 mensajes para Flash)
      const recentMessages = conversation.messages.slice(-4);
      const conversationHistory = recentMessages
        .map(msg => `${msg.role === 'user' ? 'SPAMMER' : 'TÚ'}: ${msg.content}`)
        .join('\\n');

      const fullPrompt = `${systemPrompt}

${conversationHistory ? `CONVERSACIÓN RECIENTE:\\n${conversationHistory}\\n` : ''}

SPAMMER: ${userMessage}

TÚ:`;

      console.log(`🤖 Enviando a ${this.model}...`);

      // Petición optimizada para Gemini 1.5 Flash
      const response = await axios.post(
        `${this.apiUrl}?key=${this.apiKey}`,
        {
          contents: [{
            parts: [{
              text: fullPrompt
            }]
          }],
          generationConfig: {
            temperature: 0.8,        // Optimizado para Flash
            topK: 20,               // Más eficiente
            topP: 0.9,
            maxOutputTokens: 150,   // Respuestas más concisas
            stopSequences: ["SPAMMER:", "TÚ:"] // Evitar que se confunda
          },
          safetySettings: [
            {
              category: "HARM_CATEGORY_HARASSMENT",
              threshold: "BLOCK_MEDIUM_AND_ABOVE"
            },
            {
              category: "HARM_CATEGORY_HATE_SPEECH",
              threshold: "BLOCK_MEDIUM_AND_ABOVE"
            }
          ]
        },
        {
          headers: {
            'Content-Type': 'application/json',
          },
          timeout: 15000 // Flash es más rápido, pero damos margen
        }
      );

      // Extraer respuesta
      const aiResponse = response.data?.candidates?.[0]?.content?.parts?.[0]?.text;

      if (!aiResponse) {
        throw new Error('Respuesta vacía de Gemini');
      }

      // Limpiar respuesta (quitar prefijos si los hay)
      const cleanResponse = aiResponse
        .replace(/^(TÚ:|RESPUESTA:|Manolo:|Paquita:|Roberto:)/i, '')
        .trim();

      // Guardar mensajes en la conversación
      conversation.messages.push(
        { role: 'user', content: userMessage, timestamp: new Date() },
        { role: 'assistant', content: cleanResponse, timestamp: new Date() }
      );

      console.log(`✅ ${this.model} respuesta: ${cleanResponse.substring(0, 50)}...`);

      return {
        success: true,
        response: cleanResponse,
        tokensUsed: response.data?.usageMetadata?.totalTokenCount || 0
      };

    } catch (error: any) {
      console.log('❌ Error en Gemini API:', error.message);

      // Manejo de errores específicos de Flash
      if (error.response?.status === 400) {
        return {
          success: false,
          error: 'Petición inválida (verifica el modelo en .env)'
        };
      } else if (error.response?.status === 401) {
        return {
          success: false,
          error: 'API Key inválida (verifica EXPO_PUBLIC_GEMINI_API_KEY)'
        };
      } else if (error.response?.status === 429) {
        return {
          success: false,
          error: 'Límite de peticiones excedido (Flash tiene límites altos)'
        };
      }

      return {
        success: false,
        error: `Error: ${error.message}`
      };
    }
  };

  // Resto de métodos iguales...
  endConversation = (conversationId: string): void => {
    const conversation = this.conversations.get(conversationId);
    if (conversation) {
      conversation.endTime = new Date();
      console.log(`🏁 Conversación ${conversationId} finalizada`);
    }
  };

  getConversation = (conversationId: string): GeminiConversation | undefined => {
    return this.conversations.get(conversationId);
  };

  getStats = () => {
    const total = this.conversations.size;
    const active = Array.from(this.conversations.values()).filter(c => !c.endTime).length;
    const completed = total - active;

    return {
      totalConversations: total,
      activeConversations: active,
      completedConversations: completed
    };
  };
}

// Crear instancia única
export const geminiService = new GeminiService();