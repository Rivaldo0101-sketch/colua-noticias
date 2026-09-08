package com.example.coluainformativa.database;

import android.content.Context;
import android.util.Log;

public class DataSeeder {
    public static void seedIfEmpty(Context context) {
        seedIfEmpty(context, false);
    }

    public static void seedIfEmpty(Context context, boolean force) {
        try {
            AppDatabase db = AppDatabase.getDatabase(context);
            // Se realiza la siembra de datos de forma SÍNCRONA en el hilo desde donde se llama
            if (force) {
                db.sectionDao().deleteAll();
                db.agenciaDao().deleteAll();
                db.navigationDao().deleteAll(); 
            }

            if (db.sectionDao().getAllSections().isEmpty()) {
                Log.d("SEEDER", "Base de datos vacía. Iniciando siembra inicial...");

                // 1. SECCIONES
                db.sectionDao().insert(new SectionEntity("sec_home", "Inicio", "home", "Pantalla principal", "inicio", "#173789", 1, true));
                db.sectionDao().insert(new SectionEntity("sec_ahorros", "Ahorros", "ahorros", "Cuentas de ahorro", "ahorros", "#EF8819", 2, true));
                db.sectionDao().insert(new SectionEntity("sec_creditos", "Créditos", "creditos", "Líneas de crédito", "credito", "#E42A67", 3, true));
                db.sectionDao().insert(new SectionEntity("sec_seguros", "Seguros", "seguros", "Protección y vida", "seguro", "#59B8A4", 4, true));
                db.sectionDao().insert(new SectionEntity("sec_remesas", "Remesas", "remesas", "Recibe tu dinero", "remesa", "#634794", 5, true));
                db.sectionDao().insert(new SectionEntity("sec_agencias", "Agencias", "agencias", "Nuestras ubicaciones", "ubicacion", "#173789", 6, true));
                db.sectionDao().insert(new SectionEntity("sec_servicios", "Servicios Digitales", "servicios", "Banca en línea", "servicios_digitales", "#59B8A4", 7, true));
                db.sectionDao().insert(new SectionEntity("sec_beneficios", "Beneficios", "beneficios", "Valor de ser asociado", "beneficios", "#EF8819", 8, true));
                db.sectionDao().insert(new SectionEntity("sec_noticias", "Noticias", "noticias", "Actualidad COLUA", "noticias_colua", "#E42A67", 9, true));
                db.sectionDao().insert(new SectionEntity("sec_nosotros", "Nosotros", "nosotros", "Valores, objetivos, historia e información institucional de la cooperativa", "public_service", "#173789", 10, true));
                db.sectionDao().insert(new SectionEntity("sec_sostenibilidad", "Sostenibilidad Cooperativa", "sostenibilidad", "Cursos y centros de innovación de la cooperativa", "sostenibilidad_cooperativa", "#59B8A4", 11, true));

                // CONFIGURACIÓN GLOBAL
                db.globalConfigDao().setConfig(new GlobalConfigEntity("distintivo_path", "distintivo_colua"));
                db.globalConfigDao().setConfig(new GlobalConfigEntity("logo_path", "logo_composite"));

                // NAVEGACIÓN INFERIOR (5 OPCIONES - INICIO OBLIGATORIAMENTE EN EL CENTRO POSICIÓN 3)
                db.navigationDao().insert(new NavigationItemEntity("nav_servicios", "Servicios", "servicios_digitales", "sec_servicios", "BOTTOM_NAV", 1));
                db.navigationDao().insert(new NavigationItemEntity("nav_agencias", "Agencias", "ubicacion", "sec_agencias", "BOTTOM_NAV", 2));
                db.navigationDao().insert(new NavigationItemEntity("nav_home", "Inicio", "inicio", "sec_home", "BOTTOM_NAV", 3));
                db.navigationDao().insert(new NavigationItemEntity("nav_beneficios", "Beneficios", "beneficios", "sec_beneficios", "BOTTOM_NAV", 4));
                db.navigationDao().insert(new NavigationItemEntity("nav_noticias", "Noticias", "noticias_colua", "sec_noticias", "BOTTOM_NAV", 5));
                
                db.navigationDao().insert(new NavigationItemEntity("nav_nosotros", "Nosotros", "public_service", "sec_nosotros", "NAVBAR", 1));

                db.navigationDao().insert(new NavigationItemEntity("side_profile", "Mi Perfil", "ic_person", "activity_profile", "SIDEBAR", 1));
                db.navigationDao().insert(new NavigationItemEntity("side_creditos", "Créditos", "credito", "sec_creditos", "SIDEBAR", 2));
                db.navigationDao().insert(new NavigationItemEntity("side_seguros", "Seguros", "seguro", "sec_seguros", "SIDEBAR", 3));
                db.navigationDao().insert(new NavigationItemEntity("side_remesas", "Remesas", "remesa", "sec_remesas", "SIDEBAR", 4));
                db.navigationDao().insert(new NavigationItemEntity("side_ahorros", "Ahorros", "ahorros", "sec_ahorros", "SIDEBAR", 5));
                db.navigationDao().insert(new NavigationItemEntity("side_sostenibilidad", "Sostenibilidad Cooperativa", "sostenibilidad_cooperativa", "sec_sostenibilidad", "SIDEBAR", 6));
                db.navigationDao().insert(new NavigationItemEntity("side_admin", "Portal administrativo", "portal_administrativo", "dialog_admin", "SIDEBAR", 7));
                db.navigationDao().insert(new NavigationItemEntity("side_logout", "Cerrar", "cerrar", "action_logout", "SIDEBAR", 8));

                // --- 25 AGENCIAS OFICIALES ---
                db.agenciaDao().insertAll(
                    // SOLOLÁ
                    createAgencia("Agencia Corporativa", "Sololá", "Carretera Interamericana, Km. 138.5, Aldea San Juan Argueta, Sololá.", "7795-7795", "#E42A67", "AGENCIA", ""),
                    createAgencia("Agencia Central", "Sololá", "Camino Principal, Aldea San Juan Argueta, Sololá.", "7795-7722", "#EF8819", "AGENCIA", ""),
                    createAgencia("Plaza COLUA MICOOPE", "Sololá", "Plaza COLUA 2do. Nivel, 6ta. Avenida 7-47 Zona 2, Sololá.", "7762-3180", "#EF8819", "AGENCIA", ""),
                    createAgencia("El Calvario", "Sololá", "7ma. Avenida, 6ta. Calle esquina, Zona 2, Barrio El Calvario, Sololá.", "4931-5495", "#634794", "AGENCIA", ""),
                    createAgencia("San Bartolo", "Sololá", "11 Calle 8-04, Zona 2, Barrio San Bartolo, Sololá.", "7795-7723", "#59B8A4", "AGENCIA", ""),
                    createAgencia("Concepción", "Sololá", "Sector Chuicumes I, Zona 0, Calle Principal Concepción, Sololá.", "7795-7735", "#E42A67", "AGENCIA", ""),
                    createAgencia("Los Encuentros", "Sololá", "Carretera Interamericana, Caserío Central Aldea Los Encuentros, Sololá.", "5829-2086", "#EF8819", "AGENCIA", ""),
                    createAgencia("Panajachel", "Sololá", "0 Avenida, Calle del Estadio, 0-74, Zona 1, Panajachel.", "7795-7718", "#59B8A4", "AGENCIA", ""),
                    createAgencia("San Andrés Semetabaj", "Sololá", "Barrio Tzanjuyu, San Andrés Semetabaj.", "7795-7733", "#634794", "AGENCIA", ""),
                    createAgencia("Santiago Atitlán", "Sololá", "3ra. Calle 0-58, Cantón Tzanjuyu, Zona 1, Santiago Atitlán.", "7795-7720 / 5923-5086", "#59B8A4", "AGENCIA", ""),
                    createAgencia("San Pedro La Laguna", "Sololá", "Calle al Embarcadero Chuasanahi, 5-60, Zona 2, San Pedro La Laguna.", "7721-8061", "#E42A67", "AGENCIA", ""),
                    createAgencia("San Juan La Laguna", "Sololá", "4ta. Avenida Cantón Chuitinamit, Zona 2, San Juan La Laguna.", "7795-7728", "#EF8819", "AGENCIA", ""),
                    createAgencia("Santa Clara La Laguna", "Sololá", "11ra. Avenida, Zona 2, Santa Clara La Laguna.", "4928-2887", "#59B8A4", "AGENCIA", ""),
                    createAgencia("Santa Lucía Utatlán", "Sololá", "Avenida Tecún Umán, entre 2da. y 3ra. Calle, Zona 1, Santa Lucía Utatlán.", "7722-1519", "#634794", "AGENCIA", ""),
                    createAgencia("El Novillero", "Sololá", "Calle Principal, Aldea El Novillero, Santa Lucía Utatlán.", "4928-1377", "#59B8A4", "AGENCIA", ""),
                    createAgencia("Nahualá", "Sololá", "Calle Principal, 1ra. Avenida 2-05, Zona 1, Nahualá.", "7795-7713", "#E42A67", "AGENCIA", ""),
                    createAgencia("Santa Catarina Ixtahuacán", "Sololá", "Barrio Chuijuyup, frente al Mercado Municipal.", "7795-7732", "#59B8A4", "AGENCIA", ""),
                    createAgencia("Guineales", "Sololá", "Sector Campo, a un costado del Estadio Aldea Guineales.", "7795-7731", "#EF8819", "AGENCIA", ""),
                    
                    // QUICHÉ
                    createAgencia("Agencia Quiché", "Quiché", "3ra. Avenida 04-35, Zona 1, Santa Cruz del Quiché.", "7795-7730", "#634794", "AGENCIA", ""),
                    createAgencia("Agencia Chichicastenango", "Quiché", "5ta. Calle, entre 5ta y 6ta. Avenida, Chichicastenango.", "7795-7719", "#59B8A4", "AGENCIA", ""),
                    createAgencia("Agencia Joyabaj", "Quiché", "Calle Principal, Barrio La Libertad, Joyabaj.", "7795-7715", "#E42A67", "AGENCIA", ""),
                    createAgencia("Agencia Zacualpa", "Quiché", "1ra. Calle, 2da. Avenida, Zacualpa.", "5829-3158", "#EF8819", "AGENCIA", ""),
                    
                    // TOTONICAPÁN
                    createAgencia("Agencia La Esperanza", "Totonicapán", "Camino Principal, Aldea La Esperanza, Totonicapán.", "7795-7714", "#EF8819", "AGENCIA", ""),
                    createAgencia("Agencia La Concordia", "Totonicapán", "Calle Principal, Aldea La Concordia, Totonicapán.", "7795-7724", "#634794", "AGENCIA", ""),
                    
                    // SUCHITEPÉQUEZ
                    createAgencia("Agencia Santo Tomás La Unión", "Suchitepéquez", "3ra. Calle, Zona 1, entre 4ta y 5ta. Avenida, Suchitepéquez.", "7872-8526", "#59B8A4", "AGENCIA", ""),

                    // --- EJEMPLOS EXTRAS ---
                    createAgencia("Agente MICOOPE - Super La Bendición", "Sololá", "Ubicación de Ejemplo Agente", "---", "#59B8A4", "AGENTE", ""),
                    createAgencia("Agente MICOOPE - Farmacia El Ahorro", "Quiché", "Ubicación de Ejemplo Agente", "---", "#59B8A4", "AGENTE", ""),
                    createAgencia("Cajero 5B - Central", "Sololá", "Ubicación de Cajero", "---", "#173789", "CAJERO", ""),
                    createAgencia("Cajero 5B - Terminal", "Quiché", "Ubicación de Cajero", "---", "#173789", "CAJERO", ""),
                    createAgencia("Cajero 5B - Totonicapán", "Totonicapán", "Ubicación de Cajero", "---", "#173789", "CAJERO", "")
                );

                // SERVICIOS, REMESAS, ITEMS HOME
                ContentItemEntity item1 = new ContentItemEntity("item_colua_digital", "sec_servicios", "Colua Digital", "App Móvil", "Gestiona tus finanzas 24/7", "#173789", 1);
                item1.iconName = "ic_phone_android"; item1.description = "Consulta saldos, transferencias y pagos."; item1.isDraft = false;
                db.contentDao().insertItem(item1);
                ContentItemEntity itemRemesas = new ContentItemEntity("remesa_wu", "sec_remesas", "Western Union", "¡Remesas!", "Cobra tu remesa fácil", "#FFCC00", 1);
                itemRemesas.iconName = "remesa"; itemRemesas.description = "Cobro de remesas internacionales en cualquier agencia."; itemRemesas.isDraft = false;
                db.contentDao().insertItem(itemRemesas);

                ContentItemEntity h1 = new ContentItemEntity("home_ahorro", "sec_home", "Cuentas de Ahorro\nAhorro Infantil y Juvenil", "¡Ahorro!", "Seguridad para tu futuro", "#59B8A4", 1);
                h1.iconName = "ahorros"; h1.targetSectionId = "sec_ahorros"; h1.isDraft = false;
                db.contentDao().insertItem(h1);
                ContentItemEntity h2 = new ContentItemEntity("home_credito", "sec_home", "Productivo, Consumo, Vivienda, Vehículo", "¡Crédito!", "Tasas competitivas", "#173789", 2);
                h2.iconName = "credito"; h2.targetSectionId = "sec_creditos"; h2.isDraft = false;
                db.contentDao().insertItem(h2);
                ContentItemEntity h3 = new ContentItemEntity("home_seguros", "sec_home", "Seguros de Vida\nSeguros Médicos", "¡Seguros!", "Protección para tu familia", "#EF8819", 3);
                h3.iconName = "seguro"; h3.targetSectionId = "sec_seguros"; h3.isDraft = false;
                db.contentDao().insertItem(h3);
                ContentItemEntity h4 = new ContentItemEntity("home_remesas", "sec_home", "Remesas Dirigidas", "¡Remesas!", "", "#634794", 4);
                h4.iconName = "remesa"; h4.targetSectionId = "sec_remesas"; h4.isDraft = false;
                db.contentDao().insertItem(h4);

                ContentBlockEntity sloganBlock = new ContentBlockEntity("block_home_slogan", "sec_home", "TEXT", "SOMOS EL LADO HUMANO\nde los Ahorros y Créditos", 1);
                db.contentDao().insertBlock(sloganBlock);

                Log.d("SEEDER", "Siembra inicial completada exitosamente.");
            }
        } catch (Exception e) {
            Log.e("SEEDER", "Error en siembra: " + e.getMessage());
        }
    }

    private static AgenciaEntity createAgencia(String nombre, String depto, String dir, String tel, String color, String tipo, String mapUrl) {
        String fixedId = "ag_" + nombre.toLowerCase().replaceAll("[^a-z0-9]", "_");
        return new AgenciaEntity(fixedId, nombre, depto, dir, tel, color, tipo, mapUrl, true, System.currentTimeMillis());
    }
}