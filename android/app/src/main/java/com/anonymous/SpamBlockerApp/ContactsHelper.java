// ContactsHelper.java
package com.anonymous.SpamBlockerApp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

/**
 * Helper para verificar si un número está en los contactos del usuario.
 * Usa PhoneLookup API que maneja automáticamente variaciones de formato.
 */
public class ContactsHelper {
    private static final String TAG = "ContactsHelper";
    private final Context context;

    public ContactsHelper(Context context) {
        this.context = context;
    }

    /**
     * Verifica si un número está en los contactos.
     * Usa PhoneLookup.CONTENT_FILTER_URI que normaliza automáticamente.
     *
     * @param rawNumber Número en cualquier formato (+34666123456, 666123456, etc.)
     * @return ContactInfo si existe, null si no está en contactos
     */
    public ContactInfo findContactByNumber(String rawNumber) {
        if (rawNumber == null || rawNumber.isEmpty()) {
            Log.d(TAG, "Número vacío/nulo");
            return null;
        }

        // Limpiar casos especiales
        if (rawNumber.equals("Desconocido") ||
            rawNumber.equals("Privado") ||
            rawNumber.equals("Número oculto")) {
            Log.d(TAG, "Número oculto/desconocido - no puede estar en contactos");
            return null;
        }

        try {
            // PhoneLookup.CONTENT_FILTER_URI maneja automáticamente:
            // - Códigos de país (+34)
            // - Prefijos locales (666 vs 0666)
            // - Espacios y guiones
            Uri uri = Uri.withAppendedPath(
                PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            );

            String[] projection = new String[] {
                PhoneLookup._ID,
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.NUMBER,
                PhoneLookup.PHOTO_THUMBNAIL_URI
            };

            Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                null,
                null,
                null
            );

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        // Encontrado en contactos
                        long contactId = cursor.getLong(cursor.getColumnIndexOrThrow(PhoneLookup._ID));
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.DISPLAY_NAME));
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.NUMBER));

                        int photoUriIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_THUMBNAIL_URI);
                        String photoUri = (photoUriIndex != -1 && !cursor.isNull(photoUriIndex))
                            ? cursor.getString(photoUriIndex)
                            : null;

                        Log.d(TAG, "✅ Contacto encontrado: " + name + " (" + number + ")");

                        return new ContactInfo(contactId, name, number, photoUri);
                    } else {
                        Log.d(TAG, "❌ No está en contactos: " + rawNumber);
                        return null;
                    }
                } finally {
                    cursor.close();
                }
            } else {
                Log.e(TAG, "Error: cursor null al buscar contacto");
                return null;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permiso READ_CONTACTS no concedido", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error buscando contacto: " + rawNumber, e);
            return null;
        }
    }

    /**
     * Verifica si un número está en contactos (versión simple boolean)
     */
    public boolean isInContacts(String rawNumber) {
        return findContactByNumber(rawNumber) != null;
    }

    /**
     * Clase para almacenar información del contacto
     */
    public static class ContactInfo {
        public final long id;
        public final String name;
        public final String number;
        public final String photoUri;

        public ContactInfo(long id, String name, String number, String photoUri) {
            this.id = id;
            this.name = name;
            this.number = number;
            this.photoUri = photoUri;
        }

        @Override
        public String toString() {
            return "Contact{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", number='" + number + '\'' +
                '}';
        }
    }
}
