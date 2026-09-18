package com.example.coluainformativa.database;

import android.content.Context;
import android.util.Log;

public class DataSeeder {
    public static void resetUsersAndForceLogin(Context context) {
        try {
            AppDatabase db = AppDatabase.getDatabase(context);
            db.userDao().deleteAll();

            context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            Log.i("SEEDER_RESET", "✓ Base de datos de usuarios y sesiones limpiada. Se solicitará inicio de sesión.");
        } catch (Exception e) {
            Log.e("SEEDER_RESET", "Error al reiniciar sesiones de usuarios: " + e.getMessage(), e);
        }
    }

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
                db.contentDao().deleteAllItems();
                db.contentDao().deleteAllBlocks();
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

                // --- NOTICIAS INICIALES ---
                ContentItemEntity news1 = new ContentItemEntity("news_reforestacion_2026", "sec_noticias", "Jornada de Reforestación 2026", "Ver detalles completos", "Junto a nuestros asociados y voluntarios logramos plantar más de 500 árboles.", "#E42A67", 1);
                news1.description = "Junto a decenas de familias asociadas y voluntarios de nuestra cooperativa, llevamos a cabo con éxito la siembra de 500 árboles nativos en la cuenca comunitaria, protegiendo fuentes hídricas y sembrando vida para las futuras generaciones.";
                news1.imagePath = "grupo";
                news1.tags = "#COLUAVerde #ComunidadCOLUA #MICOOPE";
                news1.isFeatured = true;
                news1.isDraft = false;
                news1.likesCount = 0;
                news1.sharesCount = 0;
                news1.targetSectionId = "https://colua.com.gt/noticias/reforestacion-2026";
                db.contentDao().insertItem(news1);

                ContentItemEntity news2 = new ContentItemEntity("news_taller_finanzas", "sec_noticias", "Taller Finanzas para Emprendedores", "Ver detalles completos", "Aprende a estructurar tus costos y maximizar tus excedentes en nuestra sede central.", "#173789", 2);
                news2.description = "Aprende a estructurar tus costos y maximizar tus excedentes en nuestra sede central con capacitadores expertos de MICOOPE.";
                news2.imagePath = "sostenibilidad_cooperativa";
                news2.tags = "#Emprendedores #MICOOPE #EducacionFinanciera";
                news2.isFeatured = false;
                news2.isDraft = false;
                news2.likesCount = 0;
                news2.sharesCount = 0;
                news2.targetSectionId = "https://colua.com.gt/noticias/taller-finanzas";
                db.contentDao().insertItem(news2);

                ContentItemEntity news3 = new ContentItemEntity("news_asamblea_general", "sec_noticias", "Asamblea General de Asociados COLUA", "Ver detalles completos", "Te invitamos a participar activamente en las decisiones y crecimiento de nuestra cooperativa.", "#59B8A4", 3);
                news3.description = "Te invitamos a participar activamente en las decisiones y crecimiento de nuestra cooperativa. Revisa la agenda y los puntos a tratar en el portal.";
                news3.imagePath = "noticias_colua";
                news3.tags = "#Asamblea2026 #AsociadosCOLUA #MICOOPE";
                news3.isFeatured = false;
                news3.isDraft = false;
                news3.likesCount = 0;
                news3.sharesCount = 0;
                news3.targetSectionId = "https://colua.com.gt/noticias/asamblea-2026";
                db.contentDao().insertItem(news3);

                // --- SOSTENIBILIDAD INICIAL ---
                ContentItemEntity sostPropuesta = new ContentItemEntity("sost_propuesta", "sec_sostenibilidad", "Nuestra Propuesta de Valor", "", "\"En COLUA reconocemos tu valor como persona para alcanzar tu bienestar integral y el de tu familia.\"", "", 1);
                sostPropuesta.imagePath = "sin_conexion";
                sostPropuesta.isDraft = false;
                db.contentDao().insertItem(sostPropuesta);

                ContentItemEntity sostImpacto = new ContentItemEntity("sost_impacto", "sec_sostenibilidad", "Huella e Impacto Social (Sololá & Occidente)", "", "Bienestar comunitario durante 2025.\n\n• DIRECTAS: 22,775 personas beneficiadas\n• INDIRECTAS: 18,672 asociados y comunidad\n\nIMPACTO GLOBAL ACUMULADO:\n41,447 vidas tocadas", "", 2);
                sostImpacto.imagePath = "sin_conexion";
                sostImpacto.isDraft = false;
                db.contentDao().insertItem(sostImpacto);

                ContentItemEntity sostEducacion = new ContentItemEntity("sost_educacion", "sec_sostenibilidad", "Educación y Formación Cooperativa", "", "Empoderando a la niñez, juventud y comunidades rurales del departamento con herramientas financieras, valores y liderazgo.", "", 3);
                sostEducacion.imagePath = "sin_conexion";
                sostEducacion.isDraft = false;
                db.contentDao().insertItem(sostEducacion);

                ContentItemEntity sostWachalal = new ContentItemEntity("sost_wachalal", "sec_sostenibilidad", "Programa Wachalal", "", "2,284 Alumnos beneficiados.\n\nFormación lúdica y fomento al ahorro en Concepción, Panajachel, Santiago Atitlán, Sololá y San Juan La Laguna.", "", 4);
                sostWachalal.imagePath = "sin_conexion";
                sostWachalal.isDraft = false;
                db.contentDao().insertItem(sostWachalal);

                ContentItemEntity sostFinanciera = new ContentItemEntity("sost_financiera", "sec_sostenibilidad", "Educación Financiera", "", "1,305 Personas alcanzadas.\n\nTalleres prácticos en Nahualá, Santiago Atitlán, San Andrés Semetabaj y Concordia Totonicapán.", "", 5);
                sostFinanciera.imagePath = "sin_conexion";
                sostFinanciera.isDraft = false;
                db.contentDao().insertItem(sostFinanciera);

                ContentItemEntity sostBecas = new ContentItemEntity("sost_becas", "sec_sostenibilidad", "Becas Jóvenes Cooperativistas", "", "160 Becados.\n\nImpulso académico y mentoría solidaria para nuevas generaciones de líderes locales.", "", 6);
                sostBecas.imagePath = "sin_conexion";
                sostBecas.isDraft = false;
                db.contentDao().insertItem(sostBecas);

                ContentItemEntity sostHuellas = new ContentItemEntity("sost_huellas", "sec_sostenibilidad", "Programa Huellas", "", "436 Atendidos.\n\nFortalecimiento de valores éticos para estudiantes y colaboradores del equipo COLUA.", "", 7);
                sostHuellas.imagePath = "sin_conexion";
                sostHuellas.isDraft = false;
                db.contentDao().insertItem(sostHuellas);

                ContentItemEntity sostHistoria = new ContentItemEntity("sost_historia", "sec_sostenibilidad", "Nuestra Historia y Raíces", "", "Desde el 22 de mayo de 1965.\n\nCOLUA MICOOPE nació gracias al coraje de 25 visionarios guiados por la misionera Elena Harding. Con un aporte inicial de Q5.00 y cuotas semanales de Q0.25, demostraron que la solidaridad comunitaria es el motor financiero más poderoso.\n\n• 60 Años de solidez\n• 25 Socios pioneros\n• Q5.00 Capital semilla", "", 8);
                sostHistoria.imagePath = "sin_conexion";
                sostHistoria.isDraft = false;
                db.contentDao().insertItem(sostHistoria);

                ContentItemEntity sostUnirme = new ContentItemEntity("sost_unirme", "sec_sostenibilidad", "¿Quieres ser parte de este impacto?", "", "Conoce cómo asociarte y acceder a los programas sociales. Únete a la familia COLUA MICOOPE.", "", 9);
                sostUnirme.imagePath = "sin_conexion";
                sostUnirme.isDraft = false;
                db.contentDao().insertItem(sostUnirme);

                ContentBlockEntity sloganBlock = new ContentBlockEntity(
                        "block_home_slogan",
                        null,
                        "TEXT",
                        "SOMOS EL LADO HUMANO\nde los Ahorros y Créditos",
                        1,
                        null,
                        "SOMOS EL LADO HUMANO",
                        null,
                        null,
                        "sec_home",
                        null,
                        null,
                        "NORMAL",
                        "BOLD",
                        "CENTER"
                );
                sloganBlock.isDraft = false;
                sloganBlock.isVisible = true;
                db.contentDao().insertBlock(sloganBlock);

                ContentBlockEntity instBlock = new ContentBlockEntity(
                        "block_home_institutional_contact",
                        null,
                        "CONTAINER",
                        "Comunícate a nuestro PBX central o búscanos en nuestras redes sociales oficiales.",
                        5,
                        "distintivo_colua",
                        "SOMOS EL LADO HUMANO",
                        "PBX: 7795-7795",
                        "tel:77957795",
                        "sec_home",
                        "#173789",
                        "#173789",
                        "NORMAL",
                        "BOLD",
                        "CENTER"
                );
                instBlock.isDraft = false;
                instBlock.isVisible = true;
                db.contentDao().insertBlock(instBlock);

                Log.d("SEEDER", "Siembra inicial completada exitosamente.");
            }

            // Asegurar que la pantalla de Nosotros tenga sus bloques iniciales en la base de datos para el Portal Administrativo
            seedNosotrosBlocks(context);

        } catch (Exception e) {
            Log.e("SEEDER", "Error en siembra: " + e.getMessage());
        }
    }

    public static void seedNosotrosBlocks(Context context) {
        try {
            AppDatabase db = AppDatabase.getDatabase(context);
            if (db.contentDao().getBlocksBySection("sec_nosotros").isEmpty()) {
                // 1. Propuesta de Valor
                ContentBlockEntity b1 = new ContentBlockEntity(
                    "block_nosotros_propuesta", null, "CARD",
                    "\"En COLUA reconocemos tu valor como persona para alcanzar tu bienestar integral y el de tu familia, a través de productos y servicios financieros éticos, ágiles y accesibles, basados en el poder de la cooperación\".",
                    1, "ic_verified", "Propuesta de Valor", null, null,
                    "sec_nosotros", "#59B8A4", null, "NORMAL", "NORMAL", "LEFT"
                );
                b1.isDraft = false; b1.isVisible = true;
                db.contentDao().insertBlock(b1);

                // 2. Visión
                ContentBlockEntity b2 = new ContentBlockEntity(
                    "block_nosotros_vision", null, "CARD",
                    "\"Ser un modelo de desarrollo y sostenibilidad integral de las comunidades basado en la cooperación\".",
                    2, "ic_info", "Visión", null, null,
                    "sec_nosotros", "#173789", null, "NORMAL", "NORMAL", "LEFT"
                );
                b2.isDraft = false; b2.isVisible = true;
                db.contentDao().insertBlock(b2);

                // 3. Propósito Visionario
                ContentBlockEntity b3 = new ContentBlockEntity(
                    "block_nosotros_proposito", null, "CARD",
                    "\"Ser la cooperativa financiera que mejora la calidad de vida de sus asociados y comunidades de Guatemala\".",
                    3, "ic_campaign", "Propósito Visionario", null, null,
                    "sec_nosotros", "#E42A67", null, "NORMAL", "NORMAL", "LEFT"
                );
                b3.isDraft = false; b3.isVisible = true;
                db.contentDao().insertBlock(b3);

                // 4. Título Valores
                ContentBlockEntity b4 = new ContentBlockEntity(
                    "block_nosotros_valores_title", null, "TEXT",
                    "",
                    4, null, "Valores de COLUA MICOOPE", null, null,
                    "sec_nosotros", null, null, "NORMAL", "BOLD", "CENTER"
                );
                b4.isDraft = false; b4.isVisible = true;
                db.contentDao().insertBlock(b4);

                // 5. Integridad
                ContentBlockEntity b5 = new ContentBlockEntity(
                    "block_nosotros_integridad", null, "TEXT",
                    "Actuar con coherencia con nuestros valores, manteniendo transparencia en todo lo que hacemos y fomentando la cooperación en cada acción.",
                    5, null, "INTEGRIDAD", null, null,
                    "sec_nosotros", null, null, "NORMAL", "BOLD", "CENTER"
                );
                b5.isDraft = false; b5.isVisible = true;
                db.contentDao().insertBlock(b5);

                // 6. Cooperación y Responsabilidad
                ContentBlockEntity b6 = new ContentBlockEntity(
                    "block_nosotros_cooperacion", null, "TEXT",
                    "COOPERACIÓN:\nTrabajar juntos para alcanzar un objetivo común, basada en la ayuda mutua, la solidaridad y el esfuerzo compartido.\n\nRESPONSABILIDAD:\nAdministramos y cuidamos los ahorros de nuestros asociados que nos han confiado.",
                    6, null, "COOPERACIÓN Y RESPONSABILIDAD", null, null,
                    "sec_nosotros", null, null, "NORMAL", "NORMAL", "LEFT"
                );
                b6.isDraft = false; b6.isVisible = true;
                db.contentDao().insertBlock(b6);

                // 7. Gráfico valores_colua_1
                ContentBlockEntity b7 = new ContentBlockEntity(
                    "block_nosotros_grafico", null, "IMAGE",
                    "",
                    7, "valores_colua_1", "Gráfico de Valores", null, null,
                    "sec_nosotros", null, null, "NORMAL", "NORMAL", "CENTER"
                );
                b7.isDraft = false; b7.isVisible = true;
                db.contentDao().insertBlock(b7);

                // 8. Enfoque al Asociado
                ContentBlockEntity b8 = new ContentBlockEntity(
                    "block_nosotros_enfoque", null, "TEXT",
                    "El centro de atención de nuestros esfuerzos y nuestra lealtad son los asociados, a quienes entregamos siempre soluciones de calidad.",
                    8, null, "ENFOQUE AL ASOCIADO", null, null,
                    "sec_nosotros", null, null, "NORMAL", "BOLD", "CENTER"
                );
                b8.isDraft = false; b8.isVisible = true;
                db.contentDao().insertBlock(b8);
            }
        } catch (Exception ignored) {}
    }

    private static AgenciaEntity createAgencia(String nombre, String depto, String dir, String tel, String color, String tipo, String mapUrl) {
        String fixedId = "ag_" + nombre.toLowerCase().replaceAll("[^a-z0-9]", "_");
        return new AgenciaEntity(fixedId, nombre, depto, dir, tel, color, tipo, mapUrl, true, System.currentTimeMillis());
    }
}