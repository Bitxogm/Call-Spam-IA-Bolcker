// src/services/TwilioService.ts
import axios from 'axios';

interface TwilioCall {
  callSid: string;
  from: string;
  to: string;
  status: string;
}

class TwilioService {
  private readonly accountSid: string;
  private readonly authToken: string;
  public readonly phoneNumber: string;
  private readonly baseUrl: string;

  constructor() {
    this.accountSid = process.env.EXPO_PUBLIC_TWILIO_ACCOUNT_SID || '';
    this.authToken = process.env.EXPO_PUBLIC_TWILIO_AUTH_TOKEN || '';
    this.phoneNumber = process.env.EXPO_PUBLIC_TWILIO_PHONE_NUMBER || '';
    this.baseUrl = `https://api.twilio.com/2010-04-01/Accounts/${this.accountSid}`;

    console.log('📞 TwilioService inicializado');
  }

  getPhoneNumber = (): string => {
    return this.phoneNumber;
  };
  // Verificar configuración
  isConfigured = (): boolean => {
    return !!(this.accountSid && this.authToken && this.phoneNumber);
  };

  // Obtener información de la cuenta (VERSIÓN WEB)
  getAccountInfo = async () => {
    try {
      // Crear auth básico para navegador (sin Buffer)
      const credentials = `${this.accountSid}:${this.authToken}`;
      const base64Credentials = btoa(credentials); // btoa funciona en navegador

      const response = await axios.get(`${this.baseUrl}.json`, {
        headers: {
          'Authorization': `Basic ${base64Credentials}`
        }
      });

      console.log('✅ Conexión Twilio exitosa');
      return response.data;
    } catch (error) {
      console.log('❌ Error conectando con Twilio:', error);
      return null;
    }
  };

  // Test básico - hacer una llamada (VERSIÓN WEB)
  makeTestCall = async (toNumber: string) => {
    try {
      const credentials = `${this.accountSid}:${this.authToken}`;
      const base64Credentials = btoa(credentials); // btoa para navegador

      const response = await axios.post(`${this.baseUrl}/Calls.json`,
        new URLSearchParams({
          To: toNumber,
          From: this.phoneNumber,
          Url: 'http://demo.twilio.com/docs/voice.xml' // TwiML de prueba
        }),
        {
          headers: {
            'Authorization': `Basic ${base64Credentials}`,
            'Content-Type': 'application/x-www-form-urlencoded'
          }
        }
      );

      console.log('✅ Llamada de prueba iniciada:', response.data.sid);
      return response.data;
    } catch (error) {
      console.log('❌ Error en llamada de prueba:', error);
      return null;
    }
  };



  // Comentadas estas dos funciones para poder probar en el browser , se cambian por las de arriba , despues cambiar para smartphone
  // Obtener información de la cuenta
  // getAccountInfo = async () => {
  //   try {
  //     const auth = Buffer.from(`${this.accountSid}:${this.authToken}`).toString('base64');

  //     const response = await axios.get(`${this.baseUrl}.json`, {
  //       headers: {
  //         'Authorization': `Basic ${auth}`
  //       }
  //     });

  //     console.log('✅ Conexión Twilio exitosa');
  //     return response.data;
  //   } catch (error) {
  //     console.log('❌ Error conectando con Twilio:', error);
  //     return null;
  //   }
  // };

  // Test básico - hacer una llamada
  // makeTestCall = async (toNumber: string) => {
  //   try {
  //     const auth = Buffer.from(`${this.accountSid}:${this.authToken}`).toString('base64');

  //     const response = await axios.post(`${this.baseUrl}/Calls.json`, 
  //       new URLSearchParams({
  //         To: toNumber,
  //         From: this.phoneNumber,
  //         Url: 'http://demo.twilio.com/docs/voice.xml' // TwiML de prueba
  //       }),
  //       {
  //         headers: {
  //           'Authorization': `Basic ${auth}`,
  //           'Content-Type': 'application/x-www-form-urlencoded'
  //         }
  //       }
  //     );

  //     console.log('✅ Llamada de prueba iniciada:', response.data.sid);
  //     return response.data;
  //   } catch (error) {
  //     console.log('❌ Error en llamada de prueba:', error);
  //     return null;
  //   }
  // };


};

export const twilioService = new TwilioService();


