// webhook-server.js
const express = require('express');
const app = express();
app.use(express.urlencoded({ extended: true }));

// Endpoint que recibirá las llamadas de Twilio
app.post('/webhook/voice', (req, res) => {
  console.log('📞 Llamada recibida de Twilio:', req.body);
  
  // TwiML básico para prueba
  const twiml = `
    <Response>
      <Say voice="alice" language="es-MX">
        Hola, soy Roberto. ¿En qué puedo ayudarle?
      </Say>
      <Pause length="2"/>
      <Say voice="alice" language="es-MX">
        Me interesa mucho su oferta, pero necesito todos los detalles.
      </Say>
    </Response>
  `;
  
  res.type('text/xml');
  res.send(twiml);
});

app.listen(3000, () => {
  console.log('🎯 Servidor webhook corriendo en puerto 3000');
});